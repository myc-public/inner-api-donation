package ma.myc.inner.donation.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import ma.myc.inner.donation.domain.bo.DonationCategory;

import java.math.BigDecimal;

/**
 * Don cree par le donateur pour lui-meme (POST /donations/me, K4c) : pas de donateur dans le corps,
 * c'est la personne connectee (party_id du token).
 */
public record CreateMyDonationRequest(
        @NotNull DonationCategory category,
        boolean type,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {
}
