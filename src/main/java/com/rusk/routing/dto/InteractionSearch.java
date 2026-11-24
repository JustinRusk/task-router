package com.rusk.routing.dto;

import com.rusk.routing.model.Interaction;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Query parameters for listing interactions. Every filter is optional; filters that are given are combined with AND.
 *
 * @param callId       exact call ID
 * @param callerNumber exact caller number (ANI)
 * @param dialedNumber exact dialed number (DNIS)
 * @param status       RECEIVED, ROUTED, COMPLETED, or ABANDONED
 * @param queue        name of the queue the interaction was routed to
 * @param from         created at or after this instant, ISO-8601, for example 2026-11-24T00:00:00Z
 * @param to           created before this instant, ISO-8601
 * @param page         zero-based page number; defaults to 0
 * @param size         page size, 1 to 200; defaults to 50
 */
public record InteractionSearch(
		String callId,
		String callerNumber,
		String dialedNumber,
		Interaction.Status status,
		String queue,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
		@Min(0) Integer page,
		@Min(1) @Max(200) Integer size) {

	public static final int DEFAULT_SIZE = 50;

	public int pageOrDefault() {
		return page == null ? 0 : page;
	}

	public int sizeOrDefault() {
		return size == null ? DEFAULT_SIZE : size;
	}
}
