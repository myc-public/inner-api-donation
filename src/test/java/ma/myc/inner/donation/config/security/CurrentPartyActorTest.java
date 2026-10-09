package ma.myc.inner.donation.config.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Auteur d'une action pour l'audit (K4d, DA5) : lu dans le token, jamais bloquant. */
class CurrentPartyActorTest {

    private static final String ISSUER = "http://localhost:8180/realms/myc-internal";

    private final CurrentParty currentParty = new CurrentParty();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(Jwt.Builder jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt.build()));
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").issuer(ISSUER).claim("azp", "donation-backoffice");
    }

    @Test
    @DisplayName("agent: party_id, preferred_username, actor_type, team_id, azp and iss")
    void agent_allClaims() {
        authenticate(token().claim("party_id", "370a0bd8-b92c-454b-8313-30ad86f96899")
                .claim("preferred_username", "agent.casa").claim("actor_type", "internal")
                .claim("team_id", "casablanca"));

        assertThat(currentParty.actor()).isEqualTo(new Actor("370a0bd8-b92c-454b-8313-30ad86f96899", "agent.casa",
                "internal", "casablanca", "donation-backoffice", ISSUER));
    }

    @Test
    @DisplayName("admin without team, service account without party_id: missing claims are null")
    void missingClaims_null() {
        authenticate(token().claim("actor_type", "internal"));

        Actor actor = currentParty.actor();

        assertThat(actor.partyId()).isNull();
        assertThat(actor.teamId()).isNull();
        assertThat(actor.username()).isNull();
        assertThat(actor.clientId()).isEqualTo("donation-backoffice");
    }

    @Test
    @DisplayName("team_id as a list (person in two teams): first value")
    void teamIdList_firstValue() {
        authenticate(token().claim("team_id", List.of("rabat", "casablanca")));

        assertThat(currentParty.actor().teamId()).isEqualTo("rabat");
    }

    @Test
    @DisplayName("no bearer token: unknown actor, no exception")
    void noToken_unknown() {
        assertThat(currentParty.actor()).isEqualTo(Actor.UNKNOWN);
    }
}
