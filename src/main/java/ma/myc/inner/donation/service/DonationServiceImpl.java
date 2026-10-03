package ma.myc.inner.donation.service;

import jakarta.transaction.Transactional;
import ma.myc.inner.donation.domain.bo.DonationBO;
import ma.myc.inner.donation.domain.bo.DonorBO;
import ma.myc.inner.donation.domain.dto.CreateDonationRequest;
import ma.myc.inner.donation.domain.dto.CreateMyDonationRequest;
import ma.myc.inner.donation.domain.dto.DonationResponse;
import ma.myc.inner.donation.domain.dto.DonorSnapshot;
import ma.myc.inner.donation.domain.dto.UpdateDonationRequest;
import ma.myc.inner.donation.events.DonationEventMapper;
import ma.myc.inner.donation.mapper.DonationMapper;
import ma.myc.inner.donation.exception.DonorProfileRequiredException;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.outbox.OutboxEventRepository;
import ma.myc.inner.donation.outbox.OutboxFactory;
import ma.myc.inner.donation.repository.DonationRepository;
import ma.myc.inner.donation.repository.DonorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DonationServiceImpl implements DonationService {

    private static final Logger log = LoggerFactory.getLogger(DonationServiceImpl.class);

    private final DonationRepository donationRepository;
    private final DonorRepository donorRepository;
    private final DonationMapper donationMapper;

    private final DonationEventMapper donationEventMapper;
    private final OutboxFactory outboxFactory;
    private final OutboxEventRepository outboxEventRepository;

    private final String donationTopic;
    private final String producerName;

    public DonationServiceImpl(DonationRepository donationRepository,
                               DonorRepository donorRepository,
                               DonationMapper donationMapper,
                               DonationEventMapper donationEventMapper,
                               OutboxFactory outboxFactory,
                               OutboxEventRepository outboxEventRepository,
                               @Value("${app.kafka.topics.donation-event:donation-event}") String donationTopic,
                               @Value("${spring.application.name:donation-service}") String producerName) {
        this.donationRepository = donationRepository;
        this.donorRepository = donorRepository;
        this.donationMapper = donationMapper;
        this.donationEventMapper = donationEventMapper;
        this.outboxFactory = outboxFactory;
        this.outboxEventRepository = outboxEventRepository;
        this.donationTopic = donationTopic;
        this.producerName = producerName;
    }


    @Override
    public DonationResponse create(CreateDonationRequest request) {

        log.info("Create donation donorId={} category={}",
                request.donor().donorId(), request.category());

        // Le donateur doit exister (404 sinon), verifie AVANT toute ecriture : ni don, ni evenement orphelin
        DonorBO donor = findDonor(request.donor().donorId());

        DonationBO donation = donationMapper.toBo(request);
        DonationBO saved = donationRepository.save(donation);

        // Instantane du donateur pris en base (source de verite), pas dans la requete du client
        var envelope = donationEventMapper.toDonationCreatedEnvelope(
                saved,
                toSnapshot(donor),
                producerName
        );

        // Key = donationId (ordering par donation)
        var outbox = outboxFactory.newEvent(
                donationTopic,
                saved.getId().toString(),
                envelope
        );
        outboxEventRepository.save(outbox);

        return donationMapper.toResponse(saved);
    }

    @Override
    public DonationResponse get(UUID donationId) {
        log.debug("Fetching donation donationId={}", donationId);
        return donationMapper.toResponse(findDonation(donationId));
    }

    @Override
    public List<DonationResponse> list() {
        log.debug("Listing donations");
        return donationRepository.findAll().stream().map(donationMapper::toResponse).toList();
    }

    @Override
    public List<DonationResponse> listByDonor(UUID donorId) {
        log.debug("Listing donations by donorId={}", donorId);
        return donationRepository.findByDonorId(donorId).stream().map(donationMapper::toResponse).toList();
    }

    @Override
    public DonationResponse createForDonor(UUID donorId, CreateMyDonationRequest request) {
        // Le profil donateur doit exister (409 sinon, DC4) : il est cree par l'onboarding (POST /donors/me)
        if (!donorRepository.existsById(donorId)) {
            throw new DonorProfileRequiredException();
        }
        return create(new CreateDonationRequest(request.category(), request.type(), request.amount(),
                new DonorSnapshot(donorId, null, null)));
    }

    @Override
    public DonationResponse getForDonor(UUID donationId, UUID donorId) {
        log.debug("Fetching own donation donationId={}", donationId);
        // Don inexistant ou d'un autre donateur : meme 404 (DC3), aucune information sur l'existence
        return donationRepository.findByIdAndDonorId(donationId, donorId)
                .map(donationMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Donation not found: " + donationId));
    }

    @Override
    public DonationResponse update(UUID donationId, UpdateDonationRequest request) {
        log.info("Updating donation donationId={}", donationId);
        DonationBO donation = findDonation(donationId);
        donationMapper.patch(donation, request);
        return donationMapper.toResponse(donationRepository.save(donation));
    }

    @Override
    public void delete(UUID donationId) {
        log.info("Deleting donation donationId={}", donationId);
        donationRepository.delete(findDonation(donationId));
    }

    private DonationBO findDonation(UUID donationId) {
        return donationRepository.findById(donationId)
                .orElseThrow(() -> new NotFoundException("Donation not found: " + donationId));
    }

    private DonorBO findDonor(UUID donorId) {
        return donorRepository.findById(donorId)
                .orElseThrow(() -> new NotFoundException("Donor not found: " + donorId));
    }

    private static DonorSnapshot toSnapshot(DonorBO donor) {
        return new DonorSnapshot(donor.getId(), donor.getDateOfBirth(), donor.getCountry());
    }

}