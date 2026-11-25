package com.rusk.routing.service;

import com.rusk.routing.dto.CreateInteractionRequest;
import com.rusk.routing.dto.InteractionPage;
import com.rusk.routing.dto.InteractionSearch;
import com.rusk.routing.exception.ConflictException;
import com.rusk.routing.exception.NoMatchingRuleException;
import com.rusk.routing.exception.NotFoundException;
import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.RoutingRule;
import com.rusk.routing.repository.InteractionRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
public class InteractionService {

	private static final Logger log = LoggerFactory.getLogger(InteractionService.class);

	private final InteractionRepository repository;
	private final RuleCache ruleCache;
	private final RoutingEngine routingEngine;
	private final TransactionTemplate transactionTemplate;
	private final DatabaseCircuitBreaker databaseCircuit;

	public InteractionService(InteractionRepository repository,
							  RuleCache ruleCache,
							  RoutingEngine routingEngine,
							  PlatformTransactionManager transactionManager,
							  DatabaseCircuitBreaker databaseCircuit) {
		this.repository = repository;
		this.ruleCache = ruleCache;
		this.routingEngine = routingEngine;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.databaseCircuit = databaseCircuit;
	}

	/**
	 * Creates an interaction without routing it. Idempotent on the call ID.
	 *
	 * @throws ConflictException if the call ID was already used with different numbers
	 */
	public CreateResult create(CreateInteractionRequest request) {
		return createIdempotently(request, repository::saveAndFlush);
	}

	/**
	 * Creates an interaction and routes it, the way an IVR would request a route. Idempotent on the call ID:
	 * a retry returns the original interaction and routing decision without evaluating the rules again.
	 * If no rule matches, nothing is saved, so a retry evaluates the rules again.
	 * <p>
	 * If the database is unreachable, the call is still routed from the in-memory rules and the decision is
	 * returned unsaved (see {@link #routeWithoutSaving}), because a caller must never wait on the database.
	 *
	 * @throws ConflictException       if the call ID was already used with different numbers
	 * @throws NoMatchingRuleException if no enabled rule matches
	 */
	public CreateResult createAndRoute(CreateInteractionRequest request) {
		if (databaseCircuit.isOpen()) {
			return routeWithoutSaving(request);
		}
		try {
			CreateResult result = createIdempotently(request, interaction -> {
				applyRouting(interaction);
				return repository.saveAndFlush(interaction);
			});
			databaseCircuit.recordSuccess();
			return result;
		} catch (RuntimeException e) {
			if (!DatabaseCircuitBreaker.isDatabaseUnavailable(e)) {
				throw e; // a real error in the request or the rules, such as a conflict or no matching rule
			}
			databaseCircuit.recordFailure(e);
			return routeWithoutSaving(request);
		}
	}

	/**
	 * Degraded mode for a database outage: routes from the cached rules without saving anything.
	 * <p>
	 * The returned interaction has no ID and reports {@code persisted: false}. The full decision is logged,
	 * so the call can be traced from the logs. Duplicate call IDs cannot be detected in this mode, but a retry
	 * gets the same decision as long as the cached rules have not changed.
	 */
	private CreateResult routeWithoutSaving(CreateInteractionRequest request) {
		Interaction interaction = newInteraction(request);
		applyRouting(interaction);
		interaction.markNotPersisted();
		log.warn("UNSAVED routing decision (database unavailable): callId={} caller={} dialed={} dtmf={} "
						+ "queue={} rule={} routedAt={}",
				interaction.getCallId(), interaction.getCallerNumber(), interaction.getDialedNumber(),
				interaction.getDtmfInput(), interaction.getRoutedQueue().getName(),
				interaction.getMatchedRule().getName(), interaction.getRoutedAt());
		return new CreateResult(interaction, false);
	}

	/**
	 * Routes an interaction to a queue using the first matching rule.
	 * Idempotent: if the interaction is already routed, its existing decision is returned unchanged.
	 *
	 * @throws NotFoundException       if the interaction does not exist
	 * @throws ConflictException       if the interaction ended without ever being routed
	 * @throws NoMatchingRuleException if no enabled rule matches
	 */
	@Transactional
	public Interaction route(Long id) {
		Interaction interaction = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new NotFoundException("Interaction " + id + " not found"));

		if (interaction.getRoutedQueue() != null) {
			return interaction; // already routed, for example by an earlier attempt that timed out
		}
		if (interaction.getStatus() != Interaction.Status.RECEIVED) {
			throw new ConflictException(
					"Interaction " + id + " cannot be routed because its status is " + interaction.getStatus());
		}

		applyRouting(interaction);
		return interaction; // saved on commit by JPA dirty checking
	}

	@Transactional(readOnly = true)
	public Interaction get(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new NotFoundException("Interaction " + id + " not found"));
	}

	/** Returns one page of interactions matching the search filters, newest first. */
	@Transactional(readOnly = true)
	public InteractionPage search(InteractionSearch search) {
		PageRequest pageRequest = PageRequest.of(search.pageOrDefault(), search.sizeOrDefault(),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		return InteractionPage.from(repository.findAll(matching(search), pageRequest));
	}

	/** Builds a WHERE clause from the filters that were given; filters left out do not restrict the results. */
	private static Specification<Interaction> matching(InteractionSearch search) {
		return (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (search.callId() != null) {
				predicates.add(cb.equal(root.get("callId"), search.callId()));
			}
			if (search.callerNumber() != null) {
				predicates.add(cb.equal(root.get("callerNumber"), search.callerNumber()));
			}
			if (search.dialedNumber() != null) {
				predicates.add(cb.equal(root.get("dialedNumber"), search.dialedNumber()));
			}
			if (search.status() != null) {
				predicates.add(cb.equal(root.get("status"), search.status()));
			}
			if (search.queue() != null) {
				predicates.add(cb.equal(root.get("routedQueue").get("name"), search.queue()));
			}
			if (search.from() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), search.from()));
			}
			if (search.to() != null) {
				predicates.add(cb.lessThan(root.get("createdAt"), search.to()));
			}
			return cb.and(predicates.toArray(Predicate[]::new));
		};
	}

	/**
	 * Returns the existing interaction for the request's call ID, or creates one with {@code saver}.
	 * <p>
	 * The lookup handles ordinary retries. Two identical requests arriving at the same moment can both miss
	 * the lookup; the unique constraint on call_id then rejects the second insert, its transaction rolls back,
	 * and it returns the row the first request saved. The fallback read runs in a new transaction because
	 * PostgreSQL rejects further statements in a transaction that hit an error.
	 */
	private CreateResult createIdempotently(CreateInteractionRequest request,
											Function<Interaction, Interaction> saver) {
		try {
			return transactionTemplate.execute(status -> findExisting(request)
					.map(existing -> new CreateResult(existing, false))
					.orElseGet(() -> new CreateResult(saver.apply(newInteraction(request)), true)));
		} catch (DataIntegrityViolationException e) {
			return transactionTemplate.execute(status -> findExisting(request)
					.map(existing -> new CreateResult(existing, false))
					.orElseThrow(() -> e)); // a different constraint failed, so it was not a duplicate call ID
		}
	}

	/** Finds the interaction for this call ID, rejecting reuse of the ID with different call details. */
	private Optional<Interaction> findExisting(CreateInteractionRequest request) {
		return repository.findByCallId(request.callId()).map(existing -> {
			if (!existing.hasSameDetails(request.callerNumber(), request.dialedNumber(), request.dtmfInput())) {
				throw new ConflictException("Call ID " + request.callId()
						+ " was already used with a different caller number, dialed number, or DTMF input");
			}
			return existing;
		});
	}

	private static Interaction newInteraction(CreateInteractionRequest request) {
		return new Interaction(request.callId(), request.callerNumber(), request.dialedNumber(), request.dtmfInput());
	}

	/** Runs the routing engine against the cached rules and records the decision on the interaction. */
	private void applyRouting(Interaction interaction) {
		RoutingRule rule = routingEngine.selectRule(interaction, ruleCache.current())
				.orElseThrow(() -> new NoMatchingRuleException(
						"No routing rule matched caller " + interaction.getCallerNumber()
								+ " dialing " + interaction.getDialedNumber()));
		interaction.routeTo(rule);
	}
}
