package com.rusk.routing.service;

import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.Queue;
import com.rusk.routing.model.RoutingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain unit tests: no Spring context and no database. */
class RoutingEngineTest {

	private static final String BILLING_LINE = "+18005550100";
	private static final String TECH_LINE = "+18005550111";
	private static final String SPANISH_LINE = "+18005550199";

	private final RoutingEngine engine = new RoutingEngine();

	private final Queue billing = new Queue("billing");
	private final Queue techSupport = new Queue("tech-support");
	private final Queue spanish = new Queue("spanish");
	private final Queue general = new Queue("general");

	private final RoutingRule spanishRule = new RoutingRule("Spanish line", 10, SPANISH_LINE, null, spanish);
	private final RoutingRule billingRule = new RoutingRule("Billing line", 20, BILLING_LINE, null, billing);
	private final RoutingRule techRule = new RoutingRule("Tech line, Austin area", 30, TECH_LINE, "+1512", techSupport);
	private final RoutingRule catchAll = new RoutingRule("Catch-all", 1000, null, null, general);

	private final List<RoutingRule> sampleRules = List.of(spanishRule, billingRule, techRule, catchAll);

	private Optional<RoutingRule> route(String caller, String dialed, List<RoutingRule> rules) {
		return engine.selectRule(new Interaction(caller, dialed), rules);
	}

	@Test
	void dialedNumberRuleMatches() {
		assertThat(route("+12145550000", BILLING_LINE, sampleRules)).contains(billingRule);
	}

	@Test
	void ruleWithTwoConditionsMatchesWhenBothHold() {
		assertThat(route("+15125550000", TECH_LINE, sampleRules)).contains(techRule);
	}

	@Test
	void ruleWithTwoConditionsFallsThroughWhenOneFails() {
		assertThat(route("+12145550000", TECH_LINE, sampleRules)).contains(catchAll);
	}

	@Test
	void unknownNumberGoesToCatchAll() {
		assertThat(route("+12145550000", "+18005550000", sampleRules)).contains(catchAll);
	}

	@Test
	void lowestPriorityNumberWinsWhenSeveralRulesMatch() {
		RoutingRule broadEarlyRule = new RoutingRule("Everything to billing", 5, null, null, billing);
		List<RoutingRule> rules = List.of(catchAll, spanishRule, broadEarlyRule);

		assertThat(route("+12145550000", SPANISH_LINE, rules)).contains(broadEarlyRule);
	}

	@Test
	void inputOrderDoesNotMatter() {
		List<RoutingRule> reversed = List.of(catchAll, techRule, billingRule, spanishRule);

		assertThat(route("+12145550000", SPANISH_LINE, reversed)).contains(spanishRule);
	}

	@Test
	void disabledRuleIsSkipped() {
		billingRule.setEnabled(false);

		assertThat(route("+12145550000", BILLING_LINE, sampleRules)).contains(catchAll);
	}

	@Test
	void noMatchWithoutCatchAll() {
		List<RoutingRule> rules = List.of(spanishRule, billingRule);

		assertThat(route("+12145550000", "+18005550000", rules)).isEmpty();
	}

	@Test
	void noRulesMeansNoMatch() {
		assertThat(route("+12145550000", BILLING_LINE, List.of())).isEmpty();
	}
}
