package com.rusk.routing.service;

import com.rusk.routing.model.Interaction;

/**
 * Outcome of an idempotent create.
 *
 * @param interaction the new interaction, or the existing one for a repeated call ID
 * @param created     true if this request created it; false if it was a retry that returned the original
 */
public record CreateResult(Interaction interaction, boolean created) {
}
