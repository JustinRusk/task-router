package com.rusk.routing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param callId       the telephony platform's unique ID for the call; used as the idempotency key,
 *                     so retrying a request with the same call ID returns the original result
 * @param callerNumber caller's number (ANI)
 * @param dialedNumber number the caller dialed (DNIS)
 * @param dtmfInput    optional DTMF digits the caller pressed in the IVR menu, such as "2" or "1*3"
 */
public record CreateInteractionRequest(
		@NotBlank @Size(max = 64) String callId,
		@NotBlank @Size(max = 32) String callerNumber,
		@NotBlank @Size(max = 32) String dialedNumber,
		@Size(max = 32) @Pattern(regexp = "[0-9*#]+", message = "must contain only the digits 0-9, * or #")
		String dtmfInput) {
}
