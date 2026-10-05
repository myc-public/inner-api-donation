package ma.myc.inner.donation.audit;

import ma.myc.inner.donation.domain.bo.DonationBO;
import ma.myc.inner.donation.domain.bo.DonationCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Etat d'une donation a un instant donne (valeurs copiees, pas l'entite : le PATCH la modifie en place).
 * Sert de {@code snapshot} a la suppression et de base au calcul des {@code changes} a la modification.
 */
public record DonationState(DonationCategory category, boolean type, BigDecimal amount, Instant timestamp) {

    public static DonationState of(DonationBO donation) {
        return new DonationState(donation.getCategory(), donation.isType(), donation.getAmount(),
                donation.getTimestamp());
    }

    /** Champs differents entre deux etats, dans l'ordre du record ; vide si rien n'a change. */
    List<AuditEvent.Change> changesTo(DonationState after) {
        List<AuditEvent.Change> changes = new ArrayList<>();
        addIfChanged(changes, "category", category, after.category);
        addIfChanged(changes, "type", type, after.type);
        // 50 et 50.00 sont le meme montant : comparaison numerique, pas equals()
        if (amount == null || after.amount == null ? amount != after.amount : amount.compareTo(after.amount) != 0) {
            changes.add(new AuditEvent.Change("amount", text(amount), text(after.amount)));
        }
        addIfChanged(changes, "timestamp", timestamp, after.timestamp);
        return changes;
    }

    private static void addIfChanged(List<AuditEvent.Change> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new AuditEvent.Change(field, text(before), text(after)));
        }
    }

    private static String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value != null ? value.toString() : null;
    }
}
