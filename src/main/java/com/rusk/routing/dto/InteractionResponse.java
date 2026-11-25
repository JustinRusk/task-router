package com.rusk.routing.dto;

import com.rusk.routing.model.Interaction;

import java.time.Instant;

/**
 * An interaction as the API returns it. The controller maps entities to this record, so changes to the JPA
 * entities (new columns, relationships, internal fields) never leak into the API by accident.
 *
 * @param id           database ID; null when persisted is false
 * @param callId       the telephony platform's call ID (the idempotency key)
 * @param callerNumber caller's number (ANI)
 * @param dialedNumber number the caller dialed (DNIS)
 * @param dtmfInput    digits the caller pressed in the IVR menu; null if none
 * @param status       RECEIVED, ROUTED, COMPLETED or ABANDONED
 * @param createdAt    when the interaction was received
 * @param routedQueue  queue chosen by the routing engine; null until routed
 * @param matchedRule  rule that chose the queue; null until routed
 * @param routedAt     when it was routed; null until routed
 * @param persisted    false only for a decision made while the database was unavailable and not saved
 */
public record InteractionResponse(
		Long id,
		String callId,
		String callerNumber,
		String dialedNumber,
		String dtmfInput,
		Interaction.Status status,
		Instant createdAt,
		QueueResponse routedQueue,
		MatchedRuleResponse matchedRule,
		Instant routedAt,
		boolean persisted) {

	public static InteractionResponse from(Interaction interaction) {
		return new InteractionResponse(
				interaction.getId(),
				interaction.getCallId(),
				interaction.getCallerNumber(),
				interaction.getDialedNumber(),
				interaction.getDtmfInput(),
				interaction.getStatus(),
				interaction.getCreatedAt(),
				QueueResponse.from(interaction.getRoutedQueue()),
				MatchedRuleResponse.from(interaction.getMatchedRule()),
				interaction.getRoutedAt(),
				interaction.isPersisted());
	}
}
