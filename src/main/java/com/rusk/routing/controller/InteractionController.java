package com.rusk.routing.controller;

import com.rusk.routing.dto.CreateInteractionRequest;
import com.rusk.routing.dto.InteractionPage;
import com.rusk.routing.dto.InteractionSearch;
import com.rusk.routing.model.Interaction;
import com.rusk.routing.service.CreateResult;
import com.rusk.routing.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

	private final InteractionService service;

	public InteractionController(InteractionService service) {
		this.service = service;
	}

	/**
	 * Creates an interaction without routing it.
	 * Returns 201 when created, 200 with the original for a repeated call ID, or 409 if the call ID was reused
	 * with different numbers.
	 */
	@PostMapping
	public ResponseEntity<Interaction> create(@Valid @RequestBody CreateInteractionRequest request) {
		return toResponse(service.create(request));
	}

	/**
	 * Creates an interaction and routes it in one call. This is the endpoint an IVR would use.
	 * Returns 201 with the routing decision, 200 with the original decision for a repeated call ID,
	 * 409 if the call ID was reused with different numbers, or 422 if no rule matches (nothing is saved).
	 * If the database is unavailable, still routes the call and returns 200 with "persisted": false.
	 */
	@PostMapping("/route")
	public ResponseEntity<Interaction> createAndRoute(@Valid @RequestBody CreateInteractionRequest request) {
		return toResponse(service.createAndRoute(request));
	}

	/**
	 * Runs the routing engine for this interaction and returns it with its routing decision.
	 * Safe to retry: an already routed interaction is returned unchanged.
	 */
	@PostMapping("/{id}/route")
	public Interaction route(@PathVariable Long id) {
		return service.route(id);
	}

	@GetMapping("/{id}")
	public Interaction get(@PathVariable Long id) {
		return service.get(id);
	}

	/**
	 * Lists interactions newest first, one page at a time. All query parameters are optional:
	 * callId, callerNumber, dialedNumber, status, queue, from, to (ISO-8601 instants), page, and size.
	 * Example: GET /api/interactions?status=ROUTED&amp;queue=billing&amp;size=20
	 */
	@GetMapping
	public InteractionPage list(@Valid @ModelAttribute InteractionSearch search) {
		return service.search(search);
	}

	/**
	 * 201 with a Location header for a new interaction. 200 for a retry that returned the original, and for a
	 * routing decision made while the database was unavailable (that body has no id and "persisted": false).
	 */
	private static ResponseEntity<Interaction> toResponse(CreateResult result) {
		Interaction interaction = result.interaction();
		if (!result.created()) {
			return ResponseEntity.ok(interaction);
		}
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/interactions/{id}")
				.buildAndExpand(interaction.getId())
				.toUri();
		return ResponseEntity.created(location).body(interaction);
	}
}
