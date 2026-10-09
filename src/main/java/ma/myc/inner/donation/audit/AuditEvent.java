package ma.myc.inner.donation.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import ma.myc.inner.donation.config.security.Actor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evenement d'audit (DA3), document plat indexe tel quel par Elasticsearch : qui a fait quoi, et quand.
 * Contrat : {@code src/main/resources/events/audit-recorded-v1.schema.json}. Aucune donnee personnelle du donateur.
 *
 * @param snapshot etat de la donation supprimee ({@code DonationDeleted} uniquement)
 * @param changes  champs modifies, avant / apres ({@code DonationUpdated} uniquement)
 */
public record AuditEvent(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        Actor actor,
        String action,
        Resource resource,
        @JsonInclude(JsonInclude.Include.NON_NULL) DonationState snapshot,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Change> changes,
        String traceId
) {

    /** Ressource concernee ; donorId est pseudonyme (aucune donnee personnelle). */
    public record Resource(String type, UUID id, UUID donorId) {
    }

    /** Valeurs en texte : un meme champ Elasticsearch garde un seul type, quel que soit le champ modifie. */
    public record Change(String field, String before, String after) {
    }
}
