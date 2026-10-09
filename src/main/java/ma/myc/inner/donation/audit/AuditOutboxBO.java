package ma.myc.inner.donation.audit;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la table {@code audit_outbox} (K4d, DA2) : insertion seule, jamais relue ni modifiee par l'API.
 * Le CDC publie la table vers {@code audit.events} ; le payload est le document JSON {@link AuditEvent}.
 */
@Entity
@Table(name = "audit_outbox")
public class AuditOutboxBO {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_type", nullable = false, updatable = false, length = 120)
    private String eventType;

    @Column(name = "event_version", nullable = false, updatable = false, length = 20)
    private String eventVersion;

    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 80)
    private String aggregateId; // donationId

    @Lob
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload; // JSON

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditOutboxBO() {
    }

    public AuditOutboxBO(UUID id, String eventType, String eventVersion, String aggregateId, String payload,
                         Instant occurredAt) {
        this.id = id;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventVersion() {
        return eventVersion;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
