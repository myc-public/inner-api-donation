package ma.myc.inner.donation.service;

import jakarta.transaction.Transactional;
import ma.myc.inner.donation.domain.bo.DonorBO;
import ma.myc.inner.donation.domain.dto.CreateDonorRequest;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.RegisterDonorRequest;
import ma.myc.inner.donation.domain.dto.UpdateDonorRequest;
import ma.myc.inner.donation.exception.DonorAlreadyExistsException;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.mapper.DonorMapper;
import ma.myc.inner.donation.repository.DonorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class DonorServiceImpl implements DonorService {

    private static final Logger log = LoggerFactory.getLogger(DonorServiceImpl.class);

    private final DonorRepository donorRepository;
    private final DonorMapper donorMapper;

    public DonorServiceImpl(DonorRepository donorRepository, DonorMapper donorMapper) {
        this.donorRepository = donorRepository;
        this.donorMapper = donorMapper;
    }


    @Override
    public DonorResponse create(CreateDonorRequest request) {
        log.info("Creating donor");

        if (donorRepository.existsByEmail(request.email())) {
            log.warn("Donor already exists (email already registered)");
            throw new DonorAlreadyExistsException();
        }
        DonorBO donor = donorMapper.toBo(request);
        donor.setCreatedAt(Instant.now());
        DonorBO saved = donorRepository.save(donor);

        return donorMapper.toResponse(saved);
    }

    @Override
    public DonorResponse registerSelf(UUID donorId, String email, RegisterDonorRequest request) {
        log.info("Registering own donor profile");
        // Une personne = un profil donateur : l'identifiant est celui de la personne (party_id), jamais genere ici
        if (donorRepository.existsById(donorId)) {
            log.warn("Donor profile already exists for this person");
            throw new DonorAlreadyExistsException(DonorAlreadyExistsException.PROFILE_MESSAGE);
        }
        if (donorRepository.existsByEmail(email)) {
            log.warn("Donor already exists (email already registered)");
            throw new DonorAlreadyExistsException();
        }
        DonorBO donor = new DonorBO(donorId, request.lastName(), request.firstName(), email,
                request.dateOfBirth(), request.country(), Instant.now());
        return donorMapper.toResponse(donorRepository.save(donor));
    }

    @Override
    public DonorResponse get(UUID donorId) {
        log.debug("Fetching donor donorId={}", donorId);
        return donorMapper.toResponse(findDonor(donorId));
    }

    @Override
    public Page<DonorResponse> list(Pageable pageable) {
        log.debug("Listing donors page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return donorRepository.findAll(pageable).map(donorMapper::toResponse);
    }

    @Override
    public DonorResponse update(UUID donorId, UpdateDonorRequest request) {
        log.info("Updating donor donorId={}", donorId);
        DonorBO donor = findDonor(donorId);

        if (request.email() != null && !request.email().equalsIgnoreCase(donor.getEmail())
                && donorRepository.existsByEmail(request.email())) {
            log.warn("Donor email update rejected (email already registered) donorId={}", donorId);
            throw new DonorAlreadyExistsException();
        }

        donorMapper.patch(donor, request);
        return donorMapper.toResponse(donorRepository.save(donor));
    }

    @Override
    public void delete(UUID donorId) {
        log.info("Deleting donor donorId={}", donorId);
        donorRepository.delete(findDonor(donorId));
    }

    private DonorBO findDonor(UUID donorId) {
        return donorRepository.findById(donorId)
                .orElseThrow(() -> new NotFoundException("Donor not found: " + donorId));
    }
}