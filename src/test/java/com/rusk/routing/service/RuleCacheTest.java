package com.rusk.routing.service;

import com.rusk.routing.model.Queue;
import com.rusk.routing.model.RoutingRule;
import com.rusk.routing.repository.RoutingRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Plain unit tests with a mocked repository: no Spring context and no database. */
class RuleCacheTest {

	private final RoutingRuleRepository repository = mock(RoutingRuleRepository.class);

	private final Queue billing = new Queue("billing");
	private final Queue general = new Queue("general");
	private final RoutingRule billingRule = new RoutingRule("Billing line", 20, "+18005550100", null, billing);
	private final RoutingRule catchAll = new RoutingRule("Catch-all", 1000, null, null, general);

	@Test
	void loadsRulesWhenCreated() {
		when(repository.findEnabledInPriorityOrder()).thenReturn(List.of(billingRule, catchAll));

		RuleCache cache = new RuleCache(repository);

		assertThat(cache.current()).containsExactly(billingRule, catchAll);
	}

	@Test
	void refreshReplacesTheRules() {
		when(repository.findEnabledInPriorityOrder())
				.thenReturn(List.of(billingRule, catchAll))
				.thenReturn(List.of(catchAll));
		RuleCache cache = new RuleCache(repository);

		cache.refresh();

		assertThat(cache.current()).containsExactly(catchAll);
	}

	@Test
	void failedRefreshKeepsThePreviousRules() {
		when(repository.findEnabledInPriorityOrder())
				.thenReturn(List.of(billingRule, catchAll))
				.thenThrow(new DataAccessResourceFailureException("database is down"));
		RuleCache cache = new RuleCache(repository);

		cache.refresh();

		assertThat(cache.current()).containsExactly(billingRule, catchAll);
	}

	@Test
	void listHandedToCallersCannotBeChanged() {
		when(repository.findEnabledInPriorityOrder()).thenReturn(List.of(billingRule));
		RuleCache cache = new RuleCache(repository);

		assertThat(cache.current()).isUnmodifiable();
	}
}
