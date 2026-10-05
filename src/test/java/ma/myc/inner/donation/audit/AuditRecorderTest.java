package ma.myc.inner.donation.audit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import ma.myc.inner.donation.config.security.Actor;
import ma.myc.inner.donation.config.security.CurrentParty;
import ma.myc.inner.donation.domain.bo.DonationBO;
import ma.myc.inner.donation.domain.bo.DonationCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Evenement d'audit (K4d, DA3) : qui a modifie / supprime quelle donation, et quand ; conforme au contrat
 * audit-recorded-v1.schema.json ; aucune donnee personnelle du donateur.
 */
@ExtendWith(MockitoExtension.class)
class AuditRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:15:00.123Z");
    private static final UUID DONATION_ID = UUID.fromString("9b2f0c1e-0000-4000-8000-000000000001");
    private static final UUID DONOR_ID = UUID.fromString("8f098e43-e3c0-4143-aac7-dc0a9a7bffbd");
    private static final Actor AGENT = new Actor("370a0bd8-b92c-454b-8313-30ad86f96899", "agent.casa", "internal",
            "casablanca", "donation-backoffice", "http://localhost:8180/realms/myc-internal");
    private static final DonationState STATE = new DonationState(DonationCategory.FOOD, true,
            new BigDecimal("50.00"), Instant.parse("2026-10-01T08:00:00Z"));

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Mock private AuditOutboxRepository auditOutboxRepository;
    @Mock private CurrentParty currentParty;

    private AuditRecorder auditRecorder;

    @BeforeEach
    void setUp() {
        auditRecorder = new AuditRecorder(auditOutboxRepository, currentParty, jsonMapper,
                Clock.fixed(NOW, ZoneOffset.UTC), Tracer.NOOP);
    }

    private AuditOutboxBO savedRow() {
        ArgumentCaptor<AuditOutboxBO> row = ArgumentCaptor.forClass(AuditOutboxBO.class);
        verify(auditOutboxRepository).save(row.capture());
        return row.getValue();
    }

    @Test
    @DisplayName("donationDeleted: row + document say who deleted which donation and when, with its last state")
    void donationDeleted_whoWhatWhen() {
        when(currentParty.actor()).thenReturn(AGENT);

        auditRecorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);

        AuditOutboxBO row = savedRow();
        assertThat(row.getEventType()).isEqualTo("DonationDeleted");
        assertThat(row.getEventVersion()).isEqualTo("1");
        assertThat(row.getAggregateId()).isEqualTo(DONATION_ID.toString());
        assertThat(row.getOccurredAt()).isEqualTo(NOW);

        JsonNode doc = jsonMapper.readTree(row.getPayload());
        assertThat(doc.get("eventId").asString()).isEqualTo(row.getId().toString());
        assertThat(doc.get("occurredAt").asString()).isEqualTo("2026-10-04T10:15:00.123Z");
        assertThat(doc.get("action").asString()).isEqualTo("donation:delete");
        assertThat(doc.at("/actor/partyId").asString()).isEqualTo(AGENT.partyId());
        assertThat(doc.at("/actor/username").asString()).isEqualTo("agent.casa");
        assertThat(doc.at("/actor/teamId").asString()).isEqualTo("casablanca");
        assertThat(doc.at("/resource/type").asString()).isEqualTo("donation");
        assertThat(doc.at("/resource/id").asString()).isEqualTo(DONATION_ID.toString());
        assertThat(doc.at("/resource/donorId").asString()).isEqualTo(DONOR_ID.toString());
        assertThat(doc.at("/snapshot/category").asString()).isEqualTo("FOOD");
        assertThat(doc.at("/snapshot/amount").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(doc.has("changes")).isFalse();
    }

    @Test
    @DisplayName("donationUpdated: only the modified fields, before / after as text ; amount 50 = 50.00")
    void donationUpdated_changesOnly() {
        when(currentParty.actor()).thenReturn(AGENT);
        DonationState after = new DonationState(DonationCategory.HEALTH, true, new BigDecimal("50"), STATE.timestamp());

        auditRecorder.donationUpdated(DONATION_ID, DONOR_ID, STATE, after);

        JsonNode doc = jsonMapper.readTree(savedRow().getPayload());
        assertThat(doc.get("eventType").asString()).isEqualTo("DonationUpdated");
        assertThat(doc.get("action").asString()).isEqualTo("donation:update");
        assertThat(doc.get("changes")).hasSize(1);
        assertThat(doc.at("/changes/0/field").asString()).isEqualTo("category");
        assertThat(doc.at("/changes/0/before").asString()).isEqualTo("FOOD");
        assertThat(doc.at("/changes/0/after").asString()).isEqualTo("HEALTH");
        assertThat(doc.has("snapshot")).isFalse();
    }

    @Test
    @DisplayName("PATCH amount 120 on a donation of 50.00: before / after in the same format (50.00 -> 120.00)")
    void donationUpdated_amountSameScale() {
        when(currentParty.actor()).thenReturn(AGENT);
        DonationBO donation = new DonationBO(DONATION_ID, DonationCategory.FOOD, true, new BigDecimal("50.00"),
                DONOR_ID, STATE.timestamp());
        DonationState before = DonationState.of(donation);
        donation.setAmount(new BigDecimal("120")); // montant tel qu'envoye par le client, avant relecture en base

        auditRecorder.donationUpdated(DONATION_ID, DONOR_ID, before, DonationState.of(donation));

        JsonNode change = jsonMapper.readTree(savedRow().getPayload()).at("/changes/0");
        assertThat(change.get("field").asString()).isEqualTo("amount");
        assertThat(change.get("before").asString()).isEqualTo("50.00");
        assertThat(change.get("after").asString()).isEqualTo("120.00");
    }

    @Test
    @DisplayName("occurredAt truncated to the millisecond, same value in the payload and in the column")
    void occurredAt_milliseconds() {
        when(currentParty.actor()).thenReturn(AGENT);
        var recorder = new AuditRecorder(auditOutboxRepository, currentParty, jsonMapper,
                Clock.fixed(Instant.parse("2026-10-05T10:38:40.083022600Z"), ZoneOffset.UTC), Tracer.NOOP);

        recorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);

        AuditOutboxBO row = savedRow();
        assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-10-05T10:38:40.083Z"));
        assertThat(jsonMapper.readTree(row.getPayload()).get("occurredAt").asString())
                .isEqualTo("2026-10-05T10:38:40.083Z");
    }

    @Test
    @DisplayName("admin without team_id, no token claims: fields are null, the action is never blocked")
    void missingClaims_areNull() {
        when(currentParty.actor()).thenReturn(Actor.UNKNOWN);

        auditRecorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);

        JsonNode actor = jsonMapper.readTree(savedRow().getPayload()).get("actor");
        assertThat(actor.get("teamId").isNull()).isTrue();
        assertThat(actor.get("partyId").isNull()).isTrue();
    }

    @Test
    @DisplayName("traceId of the current span is recorded (correlation with Tempo)")
    void traceId_recorded() {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(currentParty.actor()).thenReturn(AGENT);
        var recorder = new AuditRecorder(auditOutboxRepository, currentParty, jsonMapper,
                Clock.fixed(NOW, ZoneOffset.UTC), tracer);

        recorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);

        assertThat(jsonMapper.readTree(savedRow().getPayload()).get("traceId").asString())
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    @DisplayName("contract: every document matches audit-recorded-v1.schema.json (required fields, no extra field)")
    void documents_matchSchema() throws IOException {
        when(currentParty.actor()).thenReturn(AGENT);
        auditRecorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);
        auditRecorder.donationUpdated(DONATION_ID, DONOR_ID, STATE,
                new DonationState(DonationCategory.FOOD, false, new BigDecimal("75.00"), STATE.timestamp()));

        JsonNode schema;
        try (InputStream in = getClass().getResourceAsStream("/events/audit-recorded-v1.schema.json")) {
            schema = jsonMapper.readTree(in);
        }
        ArgumentCaptor<AuditOutboxBO> rows = ArgumentCaptor.forClass(AuditOutboxBO.class);
        verify(auditOutboxRepository, times(2)).save(rows.capture());
        for (AuditOutboxBO row : rows.getAllValues()) {
            assertConforms(jsonMapper.readTree(row.getPayload()), schema, "$");
        }
    }

    @Test
    @DisplayName("no personal data of the donor in the document (DA3)")
    void noPersonalData() {
        when(currentParty.actor()).thenReturn(AGENT);

        auditRecorder.donationDeleted(DONATION_ID, DONOR_ID, STATE);

        String payload = savedRow().getPayload();
        assertThat(payload).doesNotContainIgnoringCase("email").doesNotContainIgnoringCase("firstName")
                .doesNotContainIgnoringCase("lastName").doesNotContainIgnoringCase("dateOfBirth")
                .doesNotContainIgnoringCase("country");
    }

    /** Verification structurelle du schema (objets : requis + pas de champ en trop, recursif ; enum / const). */
    private static void assertConforms(JsonNode value, JsonNode schema, String path) {
        if (schema.has("enum")) {
            Set<String> allowed = new HashSet<>();
            schema.get("enum").forEach(v -> allowed.add(v.asString()));
            assertThat(allowed).as(path).contains(value.asString());
        }
        if (schema.has("const")) {
            assertThat(value.asString()).as(path).isEqualTo(schema.get("const").asString());
        }
        if (schema.has("properties")) {
            JsonNode properties = schema.get("properties");
            schema.path("required").forEach(r -> assertThat(value.has(r.asString())).as(path + "." + r.asString()).isTrue());
            for (String field : value.propertyNames()) {
                assertThat(properties.has(field)).as(path + "." + field + " not in schema").isTrue();
                JsonNode child = value.get(field);
                if (!child.isNull()) {
                    assertConforms(child, properties.get(field), path + "." + field);
                }
            }
        }
        if (schema.has("items")) {
            value.forEach(item -> assertConforms(item, schema.get("items"), path + "[]"));
        }
    }
}
