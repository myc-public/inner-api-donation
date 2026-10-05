package ma.myc.inner.donation.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditOutboxRepository extends JpaRepository<AuditOutboxBO, UUID> {

}
