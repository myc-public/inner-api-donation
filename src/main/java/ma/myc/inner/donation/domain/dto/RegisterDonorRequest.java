package ma.myc.inner.donation.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Creation de SON profil donateur (POST /donors/me, K4c) : ni identifiant ni email dans le corps.
 * donorId = party_id du token, email = email verifie du token.
 */
public record RegisterDonorRequest(
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank @Size(max = 120) String firstName,
        LocalDate dateOfBirth,
        @Size(max = 80) String country
) {
}
