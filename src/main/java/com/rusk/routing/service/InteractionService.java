package com.rusk.routing.service;

import com.rusk.routing.dto.CreateInteractionRequest;
import com.rusk.routing.exception.ConflictException;
import com.rusk.routing.exception.NoMatchingRuleException;
import com.rusk.routing.exception.NotFoundException;
import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.RoutingRule;
import com.rusk.routing.repository.InteractionRepository;
import com.rusk.routing.repository.RoutingRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class InteractionService {

	private final InteractionRepository repository;
	private final RoutingRuleRepository ruleRepository;
	private final RoutingEngine routingEngine;

	public InteractionService(InteractionRepository repository,
							  RoutingRuleRepository ruleRepository,
							  RoutingEngine routingEngine) {
		this.repository = repository;
		this.ruleRepository = ruleRepository;
		this.routingEngine = routingEngine;
	}

	/**
	 * Routes a received interaction to a queue using the first matching rule.
	 *
	 * @throws NotFoundException       if the interaction does not exist
	 * @throws ConflictException       if the interaction is no longer in the RECEIVED state
	 * @throws NoMatchingRuleException if no enabled rule matches
	 */
	@Transactional
	public Interaction route(Long id) {
		Interaction interaction = repository.findByIdForUpdate(id)
				.orElseThrow(() -> new NotFoundException("Interaction " + id + " not found"));

		if (interaction.getStatus() != Interaction.Status.RECEIVED) {
			throw new ConflictException(
					"Interaction " + id + " cannot be routed because its status is " + interaction.getStatus());
		}

		applyRouting(interaction);
		return interaction; // saved on commit by JPA dirty checking
	}

	/**
	 * Creates an interaction and routes it in a single transaction, the way an IVR would request a route.
	 * If no rule matches, the transaction rolls back and nothing is saved.
	 *
	 * @throws NoMatchingRuleException if no enabled rule matches
	 */
	@Transactional
	public Interaction createAndRoute(CreateInteractionRequest request) {
		Interaction interaction = new Interaction(request.callerNumber(), request.dialedNumber());
		applyRouting(interaction);
		return repository.save(interaction);
	}

	@Transactional
	public Interaction create(CreateInteractionRequest request) {
		return repository.save(new Interaction(request.callerNumber(), request.dialedNumber()));
	}

	/** Runs the routing engine against the enabled rules and records the decision on the interaction. */
	private void applyRouting(Interaction interaction) {
		List<RoutingRule> rules = ruleRepository.findEnabledInPriorityOrder();
		RoutingRule rule = routingEngine.selectRule(interaction, rules)
				.orElseThrow(() -> new NoMatchingRuleException(
						"No routing rule matched caller " + interaction.getCallerNumber()
								+ " dialing " + interaction.getDialedNumber()));
		interaction.routeTo(rule);
	}

	@Transactional(readOnly = true)
	public Interaction get(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new NotFoundException("Interaction " + id + " not found"));
	}

	@Transactional(readOnly = true)
	public List<Interaction> list() {
		return repository.findAll();
	}
}
