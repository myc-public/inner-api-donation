package ma.myc.inner.donation.outbox;

/**
 * Fait technique "un evenement du domaine a ete ecrit dans l'outbox", publie par Spring Data a chaque save
 * de {@link OutboxEventBO}. Porte le contrat publie (payload JSON) : les consommateurs en process
 * (ex. metriques metier) lisent la meme chose que les consommateurs des evenements (via le CDC).
 * TODO à remplacer une fois le CDC est sur place
 */
public record OutboxEventSaved(String eventType, String aggregateType, String payload) {
}
