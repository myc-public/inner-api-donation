package ma.myc.inner.donation.repository;

import ma.myc.inner.donation.domain.bo.DonationBO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DonationRepository extends JpaRepository<DonationBO, UUID> {
    List<DonationBO> findByDonorId(UUID donorId);

    // Un don n'est visible du donateur que s'il lui appartient (K4c)
    Optional<DonationBO> findByIdAndDonorId(UUID id, UUID donorId);
}
