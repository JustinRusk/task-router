package com.rusk.routing.dto;

import com.rusk.routing.model.Interaction;
import com.rusk.routing.model.Queue;
import com.rusk.routing.model.RoutingRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionResponseTest {

	@Test
	void mapsRoutedInteractionWithQueueAndRule() {
		Interaction interaction = new Interaction("call-1", "+15125550000", "+18005550100", "1");
		interaction.routeTo(new RoutingRule("Billing menu", 15, "+18005550100", null, "1", new Queue("payments")));

		InteractionResponse response = InteractionResponse.from(interaction);

		assertThat(response.callId()).isEqualTo("call-1");
		assertThat(response.dtmfInput()).isEqualTo("1");
		assertThat(response.status()).isEqualTo(Interaction.Status.ROUTED);
		assertThat(response.routedQueue().name()).isEqualTo("payments");
		assertThat(response.matchedRule().name()).isEqualTo("Billing menu");
		assertThat(response.matchedRule().priority()).isEqualTo(15);
		assertThat(response.matchedRule().dtmfInput()).isEqualTo("1");
		assertThat(response.routedAt()).isNotNull();
		assertThat(response.persisted()).isTrue();
	}

	@Test
	void mapsUnroutedInteractionWithNullQueueAndRule() {
		InteractionResponse response = InteractionResponse.from(new Interaction("call-2", "+15125550000", "+18005550100"));

		assertThat(response.status()).isEqualTo(Interaction.Status.RECEIVED);
		assertThat(response.routedQueue()).isNull();
		assertThat(response.matchedRule()).isNull();
		assertThat(response.routedAt()).isNull();
	}

	@Test
	void carriesNotPersistedFlag() {
		Interaction interaction = new Interaction("call-3", "+15125550000", "+18005550100");
		interaction.markNotPersisted();

		assertThat(InteractionResponse.from(interaction).persisted()).isFalse();
	}
}
