package com.rusk.routing.service;

import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.RoutingRule;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Picks the routing rule for an interaction.
 * <p>
 * Pure logic with no database access, so every rule combination can be unit tested directly.
 * Rules are checked in ascending priority order and the first match wins.
 */
@Component
public class RoutingEngine {

	public Optional<RoutingRule> selectRule(Interaction interaction, Collection<RoutingRule> rules) {
		return rules.stream()
				.sorted(Comparator.comparingInt(RoutingRule::getPriority))
				.filter(rule -> rule.matches(interaction))
				.findFirst();
	}
}
