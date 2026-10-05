package ma.myc.inner.donation.config.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ma.myc.inner.donation.outbox.OutboxEventSaved;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Metriques metier derivees des evenements du domaine (pattern outbox) : une metrique metier = un evenement publie.
 * Les services ne connaissent pas la telemetrie ; seuls les evenements reellement commites sont comptes (AFTER_COMMIT) ;
 * la source est le contrat JSON publie, celui que liront les consommateurs des evenements.
 * Etiquettes a faible cardinalite uniquement : jamais d'identifiant, montants agreges (jamais par personne).
 *
 * TODO CDC : quand le CDC (autre composant) diffusera l'outbox, remplacer ce listener par un consommateur
 * des evenements qui calcule les MEMES metriques (memes noms, memes etiquettes) : dashboards et
 * alertes inchanges, couplage nul avec l'application.
 */
@Component
public class OutboxMetricsListener {

	private static final Logger log = LoggerFactory.getLogger(OutboxMetricsListener.class);
	private static final String DONATION_CREATED = "DonationCreated";
	private static final String UNKNOWN = "unknown";
	private static final String TAG_CATEGORY = "category";

	private final MeterRegistry registry;
	private final JsonMapper jsonMapper;

	public OutboxMetricsListener(MeterRegistry registry, JsonMapper jsonMapper) {
		this.registry = registry;
		this.jsonMapper = jsonMapper;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onOutboxEventSaved(OutboxEventSaved event) {
		try {
			Counter.builder("domain.events")
					.description("Evenements du domaine publies (outbox, apres commit)")
					.tag("event_type", event.eventType())
					.tag("aggregate_type", event.aggregateType())
					.register(registry)
					.increment();
			if (DONATION_CREATED.equals(event.eventType())) {
				recordDonationCreated(jsonMapper.readTree(event.payload()).path("payload"));
			}
		} catch (RuntimeException e) {
			// La telemetrie ne doit jamais casser le metier : jamais le payload dans le log (donnees personnelles)
			log.warn("Business metrics skipped for eventType={}: {}", event.eventType(), e.getClass().getSimpleName());
		}
	}

	private void recordDonationCreated(JsonNode payload) {
		String category = text(payload, TAG_CATEGORY);
		Counter.builder("donations.created")
				.description("Dons crees")
				.tag(TAG_CATEGORY, category)
				.tag("country", text(payload, "country"))
				.register(registry)
				.increment();
		JsonNode amount = payload.get("amount");
		if (amount != null && amount.isNumber()) {
			DistributionSummary.builder("donations.amount")
					.description("Montant des dons (agrege)")
					.tag(TAG_CATEGORY, category)
					.register(registry)
					.record(amount.doubleValue());
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull() || value.asString().isBlank()) ? UNKNOWN : value.asString();
	}
}
