package com.rusk.routing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A rule that sends matching interactions to a queue.
 * Rules are evaluated in ascending priority order and the first match wins.
 * A null condition matches any value.
 */
@Entity
@Table(name = "routing_rules")
@SuppressWarnings("unused") // accessors are used reflectively by Jackson (JSON responses) and JPA
public class RoutingRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	/** Lower numbers are evaluated first. Unique, so the order is never ambiguous. */
	@Column(nullable = false, unique = true)
	private int priority;

	/** Null means "any dialed number". */
	@Column(length = 32)
	private String dialedNumber;

	/** Null means "any caller". */
	@Column(length = 32)
	private String callerPrefix;

	@ManyToOne(optional = false)
	@JoinColumn(name = "target_queue_id", nullable = false)
	private Queue targetQueue;

	@Column(nullable = false)
	private boolean enabled = true;

	/** Required by JPA. */
	protected RoutingRule() {
	}

	public RoutingRule(String name, int priority, String dialedNumber, String callerPrefix, Queue targetQueue) {
		this.name = name;
		this.priority = priority;
		this.dialedNumber = dialedNumber;
		this.callerPrefix = callerPrefix;
		this.targetQueue = targetQueue;
	}

	/** True when this rule is enabled and every condition is satisfied by the interaction. */
	public boolean matches(Interaction interaction) {
		return enabled
				&& (dialedNumber == null || dialedNumber.equals(interaction.getDialedNumber()))
				&& (callerPrefix == null || interaction.getCallerNumber().startsWith(callerPrefix));
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getPriority() {
		return priority;
	}

	public String getDialedNumber() {
		return dialedNumber;
	}

	public String getCallerPrefix() {
		return callerPrefix;
	}

	public Queue getTargetQueue() {
		return targetQueue;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
