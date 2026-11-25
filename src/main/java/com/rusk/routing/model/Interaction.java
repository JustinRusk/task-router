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
import jakarta.persistence.Transient;

import java.time.Instant;
import java.util.Objects;

/**
 * A single inbound contact (for example, a phone call) that the routing engine handles.
 */
@Entity
@Table(name = "interactions")
@SuppressWarnings("unused") // some accessors are used only by JPA
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

	/** The telephony platform's unique ID for the call. Unique, so a retried request cannot create a duplicate. */
	@Column(nullable = false, unique = true, updatable = false, length = 64)
	private String callId;

	/** Caller's number (ANI). */
	@Column(nullable = false, length = 32)
	private String callerNumber;

	/** Number the caller dialed (DNIS). */
	@Column(nullable = false, length = 32)
	private String dialedNumber;

	/** DTMF digits the caller pressed in the IVR menu, such as "2" or "1*3"; null if the caller made no selection. */
	@Column(length = 32, updatable = false)
	private String dtmfInput;

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

	/**
	 * Whether this interaction is stored in the database. Not a column: it is false only for a routing decision
	 * made while the database was unavailable, and is returned in the API response so the caller knows.
	 */
	@Transient
	private boolean persisted = true;

	/** Required by JPA. */
	protected Interaction() {
	}

	public Interaction(String callId, String callerNumber, String dialedNumber) {
		this(callId, callerNumber, dialedNumber, null);
	}

	public Interaction(String callId, String callerNumber, String dialedNumber, String dtmfInput) {
		this.callId = callId;
		this.callerNumber = callerNumber;
		this.dialedNumber = dialedNumber;
		this.dtmfInput = dtmfInput;
		this.status = Status.RECEIVED;
		this.createdAt = Instant.now(); // set here, not on persist, so it is always earlier than routedAt
	}

	public Long getId() {
		return id;
	}

	public String getCallId() {
		return callId;
	}

	/** True when this interaction was created from the same caller number, dialed number, and DTMF input. */
	public boolean hasSameDetails(String callerNumber, String dialedNumber, String dtmfInput) {
		return this.callerNumber.equals(callerNumber)
				&& this.dialedNumber.equals(dialedNumber)
				&& Objects.equals(this.dtmfInput, dtmfInput);
	}

	public String getDtmfInput() {
		return dtmfInput;
	}

	public boolean isPersisted() {
		return persisted;
	}

	/** Marks a routing decision that was made while the database was unavailable and was not saved. */
	public void markNotPersisted() {
		this.persisted = false;
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
