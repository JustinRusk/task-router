package com.rusk.routing.dto;

import com.rusk.routing.model.RoutingRule;

/**
 * The rule that made a routing decision, with the conditions it matched on, so every decision can be explained.
 * The target queue is left out because it is the interaction's routedQueue.
 *
 * @param id           database ID of the rule
 * @param name         rule name, such as "Billing line"
 * @param priority     evaluation order; lower numbers are evaluated first
 * @param dialedNumber dialed number condition; null means any
 * @param callerPrefix caller number prefix condition; null means any
 * @param dtmfInput    DTMF digits condition; null means any input, or none
 */
public record MatchedRuleResponse(Long id, String name, int priority, String dialedNumber, String callerPrefix,
								  String dtmfInput) {

	/** Returns null for a null rule, so an unrouted interaction has "matchedRule": null. */
	public static MatchedRuleResponse from(RoutingRule rule) {
		return rule == null ? null : new MatchedRuleResponse(rule.getId(), rule.getName(), rule.getPriority(),
				rule.getDialedNumber(), rule.getCallerPrefix(), rule.getDtmfInput());
	}
}
