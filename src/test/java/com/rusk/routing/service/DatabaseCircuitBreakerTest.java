package com.rusk.routing.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.net.ConnectException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseCircuitBreakerTest {

	/** A clock the test can move forward. */
	static final class TestClock extends Clock {
		private Instant now = Instant.parse("2026-11-24T12:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}
	}

	private final TestClock clock = new TestClock();
	private final DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(Duration.ofSeconds(5), clock);

	@Test
	void startsClosed() {
		assertThat(breaker.isOpen()).isFalse();
	}

	@Test
	void opensAfterFailureForTheRetryWindow() {
		breaker.recordFailure(new RuntimeException("down"));

		assertThat(breaker.isOpen()).isTrue();
		clock.advance(Duration.ofSeconds(4));
		assertThat(breaker.isOpen()).isTrue();
		clock.advance(Duration.ofSeconds(1));
		assertThat(breaker.isOpen()).isFalse();
	}

	@Test
	void successClosesIt() {
		breaker.recordFailure(new RuntimeException("down"));

		breaker.recordSuccess();

		assertThat(breaker.isOpen()).isFalse();
	}

	@Test
	void recognisesConnectionFailures() {
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new CannotCreateTransactionException("no connection"))).isTrue();
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new DataAccessResourceFailureException("lost"))).isTrue();
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new RuntimeException(new ConnectException("refused")))).isTrue();
		// SQL state 08001: unable to establish a connection, wrapped the way Spring wraps driver errors
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new JpaSystemException(new RuntimeException(new SQLException("refused", "08001"))))).isTrue();
	}

	@Test
	void ignoresErrorsThatAreNotOutages() {
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new DataIntegrityViolationException("duplicate key"))).isFalse();
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(
				new SQLException("syntax error", "42601"))).isFalse();
		assertThat(DatabaseCircuitBreaker.isDatabaseUnavailable(new IllegalStateException("bug"))).isFalse();
	}
}
