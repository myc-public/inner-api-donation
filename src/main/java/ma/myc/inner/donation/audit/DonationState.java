package ma.myc.inner.donation.audit;

import ma.myc.inner.donation.domain.bo.DonationBO;
import ma.myc.inner.donation.domain.bo.DonationCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Etat d'une donation a un instant donne (valeurs copiees, pas l'entite : le PATCH la modifie en place).
 * Sert de {@code snapshot} a la suppression et de base au calcul des {@code changes} a la modification.
 */
public record DonationState(DonationCategory category, boolean type, BigDecimal amount, Instant timestamp) {

    /** Echelle de la colonne amount (DECIMAL(19,2)) : meme format avant / apres, quel que soit le montant envoye. */
    private static final int AMOUNT_SCALE = 2;

    public static DonationState of(DonationBO donation) {
        // Apres un PATCH, l'entite porte le montant tel qu'envoye (120) : normalise comme en base (120.00)
        BigDecimal amount = donation.getAmount() != null
                ? donation.getAmount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP) : null;
        return new DonationState(donation.getCategory(), donation.isType(), amount, donation.getTimestamp());
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
