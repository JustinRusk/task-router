package com.rusk.routing.service;

import com.rusk.routing.dto.CreateInteractionRequest;
import com.rusk.routing.exception.ConflictException;
import com.rusk.routing.exception.NoMatchingRuleException;
import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.Queue;
import com.rusk.routing.model.RoutingRule;
import com.rusk.routing.repository.InteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Create-and-route during a database outage. The transaction manager is mocked: making it throw
 * CannotCreateTransactionException is exactly what Spring does when no database connection can be obtained.
 */
class InteractionServiceOutageTest {

	private static final String BILLING_LINE = "+18005550100";

	private final InteractionRepository repository = mock(InteractionRepository.class);
	private final RuleCache ruleCache = mock(RuleCache.class);
	private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
	private final DatabaseCircuitBreakerTest.TestClock clock = new DatabaseCircuitBreakerTest.TestClock();
	private final DatabaseCircuitBreaker breaker = new DatabaseCircuitBreaker(Duration.ofSeconds(5), clock);

	private final InteractionService service =
			new InteractionService(repository, ruleCache, new RoutingEngine(), transactionManager, breaker);

	private final CreateInteractionRequest billingCall =
			new CreateInteractionRequest("call-1", "+12145550000", BILLING_LINE, null);

	@BeforeEach
	void rules() {
		Queue billing = new Queue("billing");
		Queue general = new Queue("general");
		when(ruleCache.current()).thenReturn(List.of(
				new RoutingRule("Billing line", 20, BILLING_LINE, null, billing),
				new RoutingRule("Catch-all", 1000, null, null, general)));
	}

	private void databaseDown() {
		when(transactionManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException("no connection"));
	}

	private void databaseUp() {
		reset(transactionManager);
		when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(repository.findByCallId(any())).thenReturn(Optional.empty());
		when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void routesWithoutSavingWhenDatabaseIsDown() {
		databaseDown();

		CreateResult result = service.createAndRoute(billingCall);

		Interaction interaction = result.interaction();
		assertThat(result.created()).isFalse();
		assertThat(interaction.isPersisted()).isFalse();
		assertThat(interaction.getId()).isNull();
		assertThat(interaction.getStatus()).isEqualTo(Interaction.Status.ROUTED);
		assertThat(interaction.getRoutedQueue().getName()).isEqualTo("billing");
		assertThat(breaker.isOpen()).isTrue();
	}

	@Test
	void skipsTheDatabaseWhileTheBreakerIsOpen() {
		databaseDown();

		service.createAndRoute(billingCall);
		service.createAndRoute(new CreateInteractionRequest("call-2", "+12145550000", BILLING_LINE, null));
		service.createAndRoute(new CreateInteractionRequest("call-3", "+12145550000", BILLING_LINE, null));

		verify(transactionManager, times(1)).getTransaction(any());
	}

	@Test
	void triesTheDatabaseAgainAfterTheRetryWindowAndSavesWhenItIsBack() {
		databaseDown();
		service.createAndRoute(billingCall);

		clock.advance(Duration.ofSeconds(5));
		databaseUp();
		CreateResult result = service.createAndRoute(new CreateInteractionRequest("call-2", "+12145550000", BILLING_LINE, null));

		assertThat(result.created()).isTrue();
		assertThat(result.interaction().isPersisted()).isTrue();
		assertThat(breaker.isOpen()).isFalse();
		verify(repository).saveAndFlush(any());
	}

	@Test
	void noMatchingRuleIsStillAnErrorDuringAnOutage() {
		databaseDown();
		when(ruleCache.current()).thenReturn(List.of());

		assertThatThrownBy(() -> service.createAndRoute(billingCall)).isInstanceOf(NoMatchingRuleException.class);
	}

	@Test
	void conflictIsNotMistakenForAnOutage() {
		databaseUp();
		when(repository.findByCallId("call-1"))
				.thenReturn(Optional.of(new Interaction("call-1", "+15125550000", BILLING_LINE)));

		assertThatThrownBy(() -> service.createAndRoute(billingCall)).isInstanceOf(ConflictException.class);
		assertThat(breaker.isOpen()).isFalse();
		verify(repository, never()).saveAndFlush(any());
	}
}
