package com.rusk.routing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.net.ConnectException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks whether the database is reachable, so routing can skip it during an outage.
 * <p>
 * After a failure that means the database is unavailable, the breaker "opens" for
 * {@code routing.db.retry-after}. While open, callers should not attempt database work, so a caller
 * waiting in the IVR is not held up by a connection attempt that will fail anyway. Once that time passes,
 * the next request tries the database again: success closes the breaker, another failure reopens it.
 */
@Component
public class DatabaseCircuitBreaker {

	private static final Logger log = LoggerFactory.getLogger(DatabaseCircuitBreaker.class);

	private final Duration retryAfter;
	private final Clock clock;
	private final AtomicReference<Instant> openUntil = new AtomicReference<>(Instant.MIN);

	@Autowired
	public DatabaseCircuitBreaker(@Value("${routing.db.retry-after:5s}") Duration retryAfter) {
		this(retryAfter, Clock.systemUTC());
	}

	DatabaseCircuitBreaker(Duration retryAfter, Clock clock) {
		this.retryAfter = retryAfter;
		this.clock = clock;
	}

	/** True while database work should be skipped. */
	public boolean isOpen() {
		return clock.instant().isBefore(openUntil.get());
	}

	/** Opens the breaker after a failure that means the database is unreachable. */
	public void recordFailure(Throwable cause) {
		Instant previous = openUntil.getAndSet(clock.instant().plus(retryAfter));
		if (previous.equals(Instant.MIN)) {
			log.error("Database unavailable; routing without saving and retrying the database every {}",
					retryAfter, cause);
		}
	}

	/** Closes the breaker after successful database work. */
	public void recordSuccess() {
		if (!openUntil.getAndSet(Instant.MIN).equals(Instant.MIN)) {
			log.info("Database reachable again; routing decisions are being saved");
		}
	}

	/**
	 * True when the exception means the database could not be reached, as opposed to an error in the
	 * request or the code. Checks the whole cause chain, because the driver's error is often wrapped.
	 */
	public static boolean isDatabaseUnavailable(Throwable exception) {
		for (Throwable t = exception; t != null; t = t.getCause()) {
			if (t instanceof CannotCreateTransactionException
					|| t instanceof DataAccessResourceFailureException
					|| t instanceof TransientDataAccessException
					|| t instanceof SQLTransientConnectionException
					|| t instanceof ConnectException) {
				return true;
			}
			// SQL state class 08 is "connection exception" in the SQL standard
			if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
				return true;
			}
		}
		return false;
	}
}
