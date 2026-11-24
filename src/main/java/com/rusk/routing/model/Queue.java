package com.rusk.routing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A destination that interactions are routed to, such as "billing" or "tech-support".
 */
@Entity
@Table(name = "queues")
@SuppressWarnings("unused") // accessors are used reflectively by Jackson (JSON responses) and JPA
public class Queue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String name;

	/** Required by JPA. */
	protected Queue() {
	}

	public Queue(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
