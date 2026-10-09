package ma.myc.inner.donation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.myc.inner.donation.config.security.CurrentParty;
import ma.myc.inner.donation.config.security.Permissions;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.RegisterDonorRequest;
import ma.myc.inner.donation.service.DonorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Profil du donateur connecte (K4c, DC1) : l'identite vient du token (party_id), jamais de l'URL ou du corps.
 */
@RestController
@RequestMapping("/api/v1/donors/me")
@Tag(name = "My donor profile", description = "Self-service donor profile (donorId = party_id of the caller)")
public class MyDonorController {

    private final DonorService donorService;
    private final CurrentParty currentParty;

    public MyDonorController(DonorService donorService, CurrentParty currentParty) {
        this.donorService = donorService;
        this.currentParty = currentParty;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.DONOR_CREATE_OWN)
    @Operation(summary = "Create my donor profile (once, at onboarding)")
    public DonorResponse register(@Valid @RequestBody RegisterDonorRequest request) {
        return donorService.registerSelf(currentParty.partyId(), currentParty.email(), request);
    }

    @GetMapping
    @PreAuthorize(Permissions.DONOR_READ_OWN)
    @Operation(summary = "Get my donor profile")
    public DonorResponse get() {
        return donorService.get(currentParty.partyId());
    }
}
