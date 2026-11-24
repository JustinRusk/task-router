package com.rusk.routing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInteractionRequest(
		@NotBlank @Size(max = 32) String callerNumber,
		@NotBlank @Size(max = 32) String dialedNumber) {
}
