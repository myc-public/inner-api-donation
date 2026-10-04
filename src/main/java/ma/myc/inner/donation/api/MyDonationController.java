package ma.myc.inner.donation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.myc.inner.donation.config.security.CurrentParty;
import ma.myc.inner.donation.config.security.Permissions;
import ma.myc.inner.donation.domain.dto.CreateMyDonationRequest;
import ma.myc.inner.donation.domain.dto.DonationResponse;
import ma.myc.inner.donation.service.DonationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Dons du donateur connecte (K4c, DC1) : l'identite vient du token (party_id), jamais de l'URL ou du corps.
 */
@RestController
@RequestMapping("/api/v1/donations/me")
@Tag(name = "My donations", description = "Self-service donations of the caller")
public class MyDonationController {

    private final DonationService donationService;
    private final CurrentParty currentParty;

    public MyDonationController(DonationService donationService, CurrentParty currentParty) {
        this.donationService = donationService;
        this.currentParty = currentParty;
    }

    @GetMapping
    @PreAuthorize(Permissions.DONATION_LIST_OWN)
    @Operation(summary = "List all my donations")
    public List<DonationResponse> list() {
        return donationService.listByDonor(currentParty.partyId());
    }

    @GetMapping("/{donationId}")
    @PreAuthorize(Permissions.DONATION_READ_OWN)
    @Operation(summary = "Get one of my donations (404 if not mine)")
    public DonationResponse get(@PathVariable UUID donationId) {
        return donationService.getForDonor(donationId, currentParty.partyId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(Permissions.DONATION_CREATE_OWN)
    @Operation(summary = "Create a donation for myself (409 if no donor profile)")
    public DonationResponse create(@Valid @RequestBody CreateMyDonationRequest request) {
        return donationService.createForDonor(currentParty.partyId(), request);
    }
}
