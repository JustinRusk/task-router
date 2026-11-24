package com.rusk.routing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single inbound contact (for example, a phone call) that the routing engine handles.
 */
@Entity
@Table(name = "interactions")
@SuppressWarnings("unused") // accessors are used reflectively by Jackson (JSON responses) and JPA
public class Interaction {

	public enum Status {
		RECEIVED,
		ROUTED,
		COMPLETED,
		ABANDONED
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Caller's number (ANI). */
	@Column(nullable = false, length = 32)
	private String callerNumber;

	/** Number the caller dialed (DNIS). */
	@Column(nullable = false, length = 32)
	private String dialedNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Status status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/** Queue chosen by the routing engine; null until routed. */
	@ManyToOne
	@JoinColumn(name = "routed_queue_id")
	private Queue routedQueue;

	/** Rule that produced the routing decision; kept so every decision can be explained. */
	@ManyToOne
	@JoinColumn(name = "matched_rule_id")
	private RoutingRule matchedRule;

	private Instant routedAt;

	/** Required by JPA. */
	protected Interaction() {
	}

	public Interaction(String callerNumber, String dialedNumber) {
		this.callerNumber = callerNumber;
		this.dialedNumber = dialedNumber;
		this.status = Status.RECEIVED;
		this.createdAt = Instant.now(); // set here, not on persist, so it is always earlier than routedAt
	}

	public Long getId() {
		return id;
	}

	public String getCallerNumber() {
		return callerNumber;
	}

	public String getDialedNumber() {
		return dialedNumber;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Queue getRoutedQueue() {
		return routedQueue;
	}

	public RoutingRule getMatchedRule() {
		return matchedRule;
	}

	public Instant getRoutedAt() {
		return routedAt;
	}

	/** Records the routing decision made by the given rule. */
	public void routeTo(RoutingRule rule) {
		this.routedQueue = rule.getTargetQueue();
		this.matchedRule = rule;
		this.routedAt = Instant.now();
		this.status = Status.ROUTED;
	}
}
