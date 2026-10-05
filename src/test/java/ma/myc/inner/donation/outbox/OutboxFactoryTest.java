package ma.myc.inner.donation.outbox;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import ma.myc.inner.donation.events.EventEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxFactoryTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T09:59:58Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    // Vrai mapper Jackson 3 (pas de mock) : on verifie la serialisation reelle de l'outbox
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private OutboxFactory outboxFactory;

    @BeforeEach
    void setUp() {
        // Pas de trace active : Tracer / Propagator NOOP (aucun header de trace)
        outboxFactory = new OutboxFactory(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), Tracer.NOOP, Propagator.NOOP);
    }

    @Test
    @DisplayName("newEvent : le contexte de trace courant est propage en header traceparent (W3C)")
    @SuppressWarnings("unchecked")
    void newEvent_injectsTraceparentWhenTraceIsActive() {
        Tracer tracer = mock(Tracer.class);
        CurrentTraceContext currentTraceContext = mock(CurrentTraceContext.class);
        TraceContext traceContext = mock(TraceContext.class);
        Propagator propagator = mock(Propagator.class);
        when(tracer.currentTraceContext()).thenReturn(currentTraceContext);
        when(currentTraceContext.context()).thenReturn(traceContext);
        doAnswer(invocation -> {
            Propagator.Setter<Map<String, String>> setter = invocation.getArgument(2);
            setter.set(invocation.getArgument(1), "traceparent", TRACEPARENT);
            return null;
        }).when(propagator).inject(eq(traceContext), any(Map.class), any(Propagator.Setter.class));
        var factory = new OutboxFactory(jsonMapper, Clock.fixed(NOW, ZoneOffset.UTC), tracer, propagator);

        OutboxEventBO event = factory.newEvent(envelope("donation-service", new Payload("donation-42", 150)));

        assertThat(jsonMapper.readTree(event.getHeaders()).get("traceparent").asString()).isEqualTo(TRACEPARENT);
    }

    @Test
    @DisplayName("newEvent : sans trace active, aucun header traceparent")
    void newEvent_omitsTraceparentWithoutActiveTrace() {
        OutboxEventBO event = outboxFactory.newEvent(envelope("donation-service", new Payload("donation-42", 150)));

        assertThat(jsonMapper.readTree(event.getHeaders()).has("traceparent")).isFalse();
    }

    @Test
    @DisplayName("newEvent : copie les metadonnees de l'enveloppe et serialise payload + headers en JSON")
    void newEvent_mapsEnvelopeAndSerializesJson() {
        OutboxEventBO event = outboxFactory.newEvent(envelope("donation-service", new Payload("donation-42", 150)));

        assertThat(event.getId()).isEqualTo(EVENT_ID);
        assertThat(event.getAggregateType()).isEqualTo("Donation");
        assertThat(event.getAggregateId()).isEqualTo("donation-42");
        assertThat(event.getEventType()).isEqualTo("DonationCreated");
        assertThat(event.getEventVersion()).isEqualTo("1");
        // Date d'ecriture en outbox = horloge injectee, pas la date metier de l'enveloppe
        assertThat(event.getOccurredAt()).isEqualTo(NOW);

        JsonNode payload = jsonMapper.readTree(event.getPayload());
        assertThat(payload.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(payload.get("payload").get("amount").asInt()).isEqualTo(150);

        JsonNode headers = jsonMapper.readTree(event.getHeaders());
        assertThat(headers.get("eventType").asString()).isEqualTo("DonationCreated");
        assertThat(headers.get("occurredAt").asString()).isEqualTo(OCCURRED_AT.toString());
        assertThat(headers.get("producer").asString()).isEqualTo("donation-service");
    }

    @Test
    @DisplayName("newEvent : le header producer est omis quand il est vide")
    void newEvent_omitsBlankProducerHeader() {
        OutboxEventBO event = outboxFactory.newEvent(envelope("  ", new Payload("donation-42", 150)));

        assertThat(jsonMapper.readTree(event.getHeaders()).has("producer")).isFalse();
    }

    @Test
    @DisplayName("newEvent : une erreur de serialisation JSON est remontee en IllegalStateException")
    void newEvent_wrapsSerializationFailure() {
        var envelope = envelope("donation-service", new FailingPayload());

        assertThatThrownBy(() -> outboxFactory.newEvent(envelope))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize event to JSON")
                .hasCauseInstanceOf(JacksonException.class);
    }

    private static EventEnvelope<Object> envelope(String producer, Object payload) {
        return new EventEnvelope<>(EVENT_ID, "DonationCreated", "1", OCCURRED_AT,
                "Donation", "donation-42", producer, payload);
    }

    record Payload(String donationId, int amount) {}

    /** Payload dont le getter echoue : force une erreur de serialisation Jackson. */
    static final class FailingPayload {
        public String getValue() {
            throw new IllegalStateException("boom");
        }
    }
}
