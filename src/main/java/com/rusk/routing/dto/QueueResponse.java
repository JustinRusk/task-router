package com.rusk.routing.dto;

import com.rusk.routing.model.Queue;

/**
 * A queue as the API returns it.
 *
 * @param id   database ID of the queue
 * @param name queue name, such as "billing"
 */
public record QueueResponse(Long id, String name) {

	/** Returns null for a null queue, so an unrouted interaction has "routedQueue": null. */
	public static QueueResponse from(Queue queue) {
		return queue == null ? null : new QueueResponse(queue.getId(), queue.getName());
	}
}
