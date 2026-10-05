package ma.myc.inner.donation.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxEventBOTest {

	@Test
	@DisplayName("domainEvents : publie OutboxEventSaved avec le type, l'agregat et le contrat JSON")
	void domainEvents_publishesOutboxEventSaved() {
		var event = new OutboxEventBO(UUID.randomUUID(), "Donation", "donation-42", "DonationCreated", "1",
				"{\"payload\":{}}", "{}", Instant.parse("2026-09-26T10:00:00Z"));

		assertThat(event.domainEvents())
				.containsExactly(new OutboxEventSaved("DonationCreated", "Donation", "{\"payload\":{}}"));
	}
}
