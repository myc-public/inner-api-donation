package ma.myc.inner.donation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.myc.inner.donation.config.security.Permissions;
import ma.myc.inner.donation.domain.dto.CreateDonationRequest;
import ma.myc.inner.donation.domain.dto.DonationResponse;
import ma.myc.inner.donation.domain.dto.UpdateDonationRequest;
import ma.myc.inner.donation.service.DonationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/donations")
@Tag(name = "Donations", description = "Donation management")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a donation")
    @PreAuthorize(Permissions.DONATION_CREATE)
    public DonationResponse create(@Valid @RequestBody CreateDonationRequest request) {
        return donationService.create(request);
    }

    @GetMapping("/{donationId}")
    @Operation(summary = "Get a donation by id")
    @PreAuthorize(Permissions.DONATION_READ)
    public DonationResponse get(@PathVariable UUID donationId) {
        return donationService.get(donationId);
    }

    @GetMapping
    @Operation(summary = "List donations")
    @PreAuthorize(Permissions.DONATION_LIST)
    public List<DonationResponse> list() {
        return donationService.list();
    }

    @GetMapping("/by-donor/{donorId}")
    @Operation(summary = "List donations by donor")
    @PreAuthorize(Permissions.DONATION_READ)
    public List<DonationResponse> listByDonor(@PathVariable UUID donorId) {
        return donationService.listByDonor(donorId);
    }

    @PatchMapping("/{donationId}")
    @Operation(summary = "Update a donation (partial)")
    @PreAuthorize(Permissions.DONATION_UPDATE)
    public DonationResponse update(@PathVariable UUID donationId, @Valid @RequestBody UpdateDonationRequest request) {
        return donationService.update(donationId, request);
    }

    @DeleteMapping("/{donationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a donation")
    @PreAuthorize(Permissions.DONATION_DELETE)
    public void delete(@PathVariable UUID donationId) {
        donationService.delete(donationId);
    }
}