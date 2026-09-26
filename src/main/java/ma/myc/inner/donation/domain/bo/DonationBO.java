package ma.myc.inner.donation.domain.bo;


import jakarta.persistence.*;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "donation",
        indexes = {
                @Index(name = "idx_donation_donor_id", columnList = "donor_id"),
                @Index(name = "idx_donation_timestamp", columnList = "donation_timestamp")
        }
)
public class DonationBO {

    // Identifiant attribue par l'application (DonationMapper.newId), comme DonorBO : pas de @GeneratedValue.
    // Avec les deux, save() fait un merge qu'Hibernate 7 rejette (ObjectOptimisticLockingFailureException).
    @Id
    @Column(name = "donation_id",  columnDefinition = "BINARY(16)",nullable = false, updatable = false)
    private UUID id;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private DonationCategory category; // FOOD, HEALTH, EDUCATION, etc.

    /**
     * true = recurring / false = one-shot (example semantics)
     */
    @Setter
    @Column(name = "type_flag", nullable = false)
    private boolean type;

    @Setter
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Donor reference (decoupled from Donor domain)
     */
    @Setter
    @Column(name = "donor_id", nullable = false)
    private UUID donorId;

    @Setter
    @Column(name = "donation_timestamp", nullable = false)
    private Instant timestamp;

    protected DonationBO() {
        // for JPA
    }

    public DonationBO(
            UUID id,
            DonationCategory category,
            boolean type,
            BigDecimal amount,
            UUID donorId,
            Instant timestamp
    ) {
        this.id = id;
        this.category = category;
        this.type = type;
        this.amount = amount;
        this.donorId = donorId;
        this.timestamp = timestamp;
    }

    public UUID getId() {
        return id;
    }

    public DonationCategory getCategory() {
        return category;
    }

    public boolean isType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public UUID getDonorId() {
        return donorId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
