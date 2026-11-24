package com.rusk.routing.exception;

/** No enabled routing rule matched the interaction. Usually means the catch-all rule is missing or disabled. */
public class NoMatchingRuleException extends RuntimeException {

	public NoMatchingRuleException(String message) {
		super(message);
	}
}
