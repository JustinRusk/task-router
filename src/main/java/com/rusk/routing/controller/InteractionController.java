package com.rusk.routing.controller;

import com.rusk.routing.dto.CreateInteractionRequest;
import com.rusk.routing.model.Interaction;
import com.rusk.routing.service.InteractionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

	private final InteractionService service;

	public InteractionController(InteractionService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<Interaction> create(@Valid @RequestBody CreateInteractionRequest request) {
		Interaction created = service.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(created.getId())
				.toUri();
		return ResponseEntity.created(location).body(created);
	}

	/**
	 * Creates an interaction and routes it in one call. This is the endpoint an IVR would use.
	 * Returns 201 with the routing decision, or 422 if no rule matches (nothing is saved in that case).
	 */
	@PostMapping("/route")
	public ResponseEntity<Interaction> createAndRoute(@Valid @RequestBody CreateInteractionRequest request) {
		Interaction routed = service.createAndRoute(request);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/interactions/{id}")
				.buildAndExpand(routed.getId())
				.toUri();
		return ResponseEntity.created(location).body(routed);
	}

	/** Runs the routing engine for this interaction and returns it with its routing decision. */
	@PostMapping("/{id}/route")
	public Interaction route(@PathVariable Long id) {
		return service.route(id);
	}

	@GetMapping("/{id}")
	public Interaction get(@PathVariable Long id) {
		return service.get(id);
	}

	@GetMapping
	public List<Interaction> list() {
		return service.list();
	}
}
