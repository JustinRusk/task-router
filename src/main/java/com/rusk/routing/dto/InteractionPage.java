package com.rusk.routing.dto;

import com.rusk.routing.model.Interaction;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * One page of interactions, newest first.
 *
 * @param items      the interactions on this page
 * @param page       zero-based page number
 * @param size       requested page size
 * @param totalItems number of interactions matching the filters, across all pages
 * @param totalPages number of pages at this size
 */
public record InteractionPage(List<InteractionResponse> items, int page, int size, long totalItems, int totalPages) {

	public static InteractionPage from(Page<Interaction> page) {
		return new InteractionPage(page.getContent().stream().map(InteractionResponse::from).toList(),
				page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
	}
}
