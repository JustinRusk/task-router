package com.rusk.routing.exception;

/** The request conflicts with the resource's current state, for example routing an interaction twice. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}
}
