package com.rusk.routing.service;

import com.rusk.routing.model.RoutingRule;
import com.rusk.routing.repository.RoutingRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory copy of the enabled routing rules, so routing a call does not query the database for rules.
 * <p>
 * The rules are loaded when the application starts, and startup fails if they cannot be loaded, because the
 * engine cannot route without them. They are then reloaded on a schedule set by
 * {@code routing.rules.refresh-interval}. A rule change therefore takes effect within one interval.
 * <p>
 * Each reload builds a complete new list and swaps it in with a single atomic step. A request that is
 * already routing keeps the list it started with, so it never sees a half-updated set of rules.
 * If a reload fails, for example because the database is down, the previous rules stay in use.
 * <p>
 * The cached rules are detached entities whose target queues were loaded in the same query, so reading them
 * never triggers a database call. They are shared by all requests and must be treated as read-only.
 */
@Component
@DependsOnDatabaseInitialization // load after any SQL init scripts, so seeded rules are present at startup
public class RuleCache {

	private static final Logger log = LoggerFactory.getLogger(RuleCache.class);

	private final RoutingRuleRepository repository;
	private final AtomicReference<List<RoutingRule>> rules = new AtomicReference<>(List.of());

	public RuleCache(RoutingRuleRepository repository) {
		this.repository = repository;
		List<RoutingRule> initial = load();
		rules.set(initial);
		log.info("Loaded {} enabled routing rules", initial.size());
	}

	/** The current enabled rules, lowest priority number first. Never null. */
	public List<RoutingRule> current() {
		return rules.get();
	}

	/** Reloads the rules from the database. On failure, logs a warning and keeps the current rules. */
	@Scheduled(fixedDelayString = "${routing.rules.refresh-interval:30s}",
			initialDelayString = "${routing.rules.refresh-interval:30s}")
	public void refresh() {
		try {
			List<RoutingRule> latest = load();
			List<RoutingRule> previous = rules.getAndSet(latest);
			log.debug("Refreshed routing rules: {} before, {} now", previous.size(), latest.size());
		} catch (RuntimeException e) {
			log.warn("Routing rule refresh failed; keeping the {} rules already loaded", rules.get().size(), e);
		}
	}

	private List<RoutingRule> load() {
		return List.copyOf(repository.findEnabledInPriorityOrder());
	}
}
