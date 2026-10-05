package ma.myc.inner.donation.audit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import ma.myc.inner.donation.config.security.CurrentParty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Audit des actions sensibles (K4d, DA1-DA7) : toute modification ou suppression d'une donation, quel que soit
 * l'auteur. Appele par le service DANS sa transaction : une ligne d'audit existe si et seulement si l'action
 * a ete commitee (pas d'audit perdu, pas d'audit fantome). L'auteur vient du token via {@link CurrentParty}.
 */
@Component
public class AuditRecorder {

    static final String EVENT_VERSION = "1";
    static final String DONATION_UPDATED = "DonationUpdated";
    static final String DONATION_DELETED = "DonationDeleted";
    private static final String RESOURCE_DONATION = "donation";

    private final AuditOutboxRepository auditOutboxRepository;
    private final CurrentParty currentParty;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final Tracer tracer;

    public AuditRecorder(AuditOutboxRepository auditOutboxRepository, CurrentParty currentParty,
                         JsonMapper jsonMapper, Clock clock, Tracer tracer) {
        this.auditOutboxRepository = auditOutboxRepository;
        this.currentParty = currentParty;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.tracer = tracer;
    }

    public void donationUpdated(UUID donationId, UUID donorId, DonationState before, DonationState after) {
        record(DONATION_UPDATED, "donation:update", donationId, donorId, null, before.changesTo(after));
    }

    public void donationDeleted(UUID donationId, UUID donorId, DonationState snapshot) {
        record(DONATION_DELETED, "donation:delete", donationId, donorId, snapshot, null);
    }

    private void record(String eventType, String action, UUID donationId, UUID donorId,
                        DonationState snapshot, List<AuditEvent.Change> changes) {
        Instant now = Instant.now(clock);
        AuditEvent event = new AuditEvent(UUID.randomUUID(), eventType, EVENT_VERSION, now, currentParty.actor(),
                action, new AuditEvent.Resource(RESOURCE_DONATION, donationId, donorId), snapshot, changes, traceId());
        auditOutboxRepository.save(new AuditOutboxBO(event.eventId(), eventType, EVENT_VERSION,
                donationId.toString(), writeJson(event), now));
    }

    private String traceId() {
        Span span = tracer.currentSpan();
        return span != null ? span.context().traceId() : null;
    }

    private String writeJson(AuditEvent event) {
        try {
            return jsonMapper.writeValueAsString(event);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize audit event to JSON", e);
        }
    }
}
