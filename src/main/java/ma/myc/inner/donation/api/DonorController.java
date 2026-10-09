package ma.myc.inner.donation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.myc.inner.donation.config.security.Permissions;
import ma.myc.inner.donation.domain.dto.CreateDonorRequest;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.UpdateDonorRequest;
import ma.myc.inner.donation.service.DonorService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/donors")
@Tag(name = "Donors", description = "Donor management")
public class DonorController {

    private final DonorService donorService;

    public DonorController(DonorService donorService) {
        this.donorService = donorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a donor")
    @PreAuthorize(Permissions.DONOR_CREATE)
    public DonorResponse create(@Valid @RequestBody CreateDonorRequest request) {
        return donorService.create(request);
    }

    @GetMapping("/{donorId}")
    @Operation(summary = "Get a donor by id")
    @PreAuthorize(Permissions.DONOR_READ)
    public DonorResponse get(@PathVariable UUID donorId) {
        return donorService.get(donorId);
    }

    @GetMapping
    @Operation(summary = "List donors (paginated)")
    @PreAuthorize(Permissions.DONOR_LIST)
    public Page<DonorResponse> list(
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC) Pageable pageable) {
        return donorService.list(pageable);
    }

    @PatchMapping("/{donorId}")
    @Operation(summary = "Update a donor (partial)")
    @PreAuthorize(Permissions.DONOR_UPDATE)
    public DonorResponse update(@PathVariable UUID donorId, @Valid @RequestBody UpdateDonorRequest request) {
        return donorService.update(donorId, request);
    }

    @DeleteMapping("/{donorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a donor")
    @PreAuthorize(Permissions.DONOR_DELETE)
    public void delete(@PathVariable UUID donorId) {
        donorService.delete(donorId);
    }
}