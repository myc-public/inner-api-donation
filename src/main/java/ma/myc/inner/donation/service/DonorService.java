package ma.myc.inner.donation.service;


import ma.myc.inner.donation.domain.dto.CreateDonorRequest;
import ma.myc.inner.donation.domain.dto.RegisterDonorRequest;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.UpdateDonorRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DonorService {
    DonorResponse create(CreateDonorRequest request);
    // Profil de la personne connectee (K4c) : donorId = party_id, email verifie du token ; une seule fois
    DonorResponse registerSelf(UUID donorId, String email, RegisterDonorRequest request);
    DonorResponse get(UUID donorId);
    Page<DonorResponse> list(Pageable pageable);
    DonorResponse update(UUID donorId, UpdateDonorRequest request);
    void delete(UUID donorId);
}