package ma.myc.inner.donation.service;

import ma.myc.inner.donation.domain.bo.DonationBO;
import ma.myc.inner.donation.domain.bo.DonationCategory;
import ma.myc.inner.donation.domain.bo.DonorBO;
import ma.myc.inner.donation.domain.dto.CreateDonationRequest;
import ma.myc.inner.donation.domain.dto.DonationResponse;
import ma.myc.inner.donation.domain.dto.DonorSnapshot;
import ma.myc.inner.donation.domain.dto.UpdateDonationRequest;
import ma.myc.inner.donation.events.DonationCreatedEventPayload;
import ma.myc.inner.donation.events.DonationEventMapper;
import ma.myc.inner.donation.events.EventEnvelope;
import ma.myc.inner.donation.mapper.DonationMapper;
import ma.myc.inner.donation.outbox.OutboxEventBO;
import ma.myc.inner.donation.outbox.OutboxEventRepository;
import ma.myc.inner.donation.outbox.OutboxFactory;
import ma.myc.inner.donation.repository.DonationRepository;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.repository.DonorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationServiceImplTest {

    @Mock private DonationRepository donationRepository;
    @Mock private DonorRepository donorRepository;
    @Mock private DonationMapper donationMapper;
    @Mock private DonationEventMapper donationEventMapper;
    @Mock private OutboxFactory outboxFactory;
    @Mock private OutboxEventRepository outboxEventRepository;

    private DonationServiceImpl donationService;

    private static final UUID DONATION_ID = UUID.randomUUID();
    private static final UUID DONOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now();
    private static final String TOPIC = "donation-event";
    private static final String PRODUCER = "donation-service";

    @BeforeEach
    void setUp() {
        donationService = new DonationServiceImpl(
                donationRepository, donorRepository, donationMapper,
                donationEventMapper, outboxFactory, outboxEventRepository,
                TOPIC, PRODUCER
        );
    }

    private DonationBO buildDonationBO() {
        return new DonationBO(DONATION_ID, DonationCategory.HEALTH, false,
                new BigDecimal("100.00"), DONOR_ID, NOW);
    }

    private DonationResponse buildDonationResponse() {
        return new DonationResponse(DONATION_ID, DonationCategory.HEALTH, false,
                new BigDecimal("100.00"), DONOR_ID, NOW);
    }

    private CreateDonationRequest buildCreateRequest() {
        var donor = new DonorSnapshot(DONOR_ID, LocalDate.of(1990, 1, 1), "MA");
        return new CreateDonationRequest(DonationCategory.HEALTH, false, new BigDecimal("100.00"), donor);
    }

    // ─── create ───────────────────────────────────────────────────────────────

    /** Donateur en base : date de naissance et pays DIFFERENTS de la requete (buildCreateRequest : 1990 / MA). */
    private static final LocalDate DB_DATE_OF_BIRTH = LocalDate.of(1985, 5, 5);
    private static final String DB_COUNTRY = "FR";

    private DonorBO buildDonorBO() {
        return new DonorBO(DONOR_ID, "Doe", "John", "john.doe@example.com", DB_DATE_OF_BIRTH, DB_COUNTRY, NOW);
    }

    /** Stubs du chemin nominal de create ; renvoie l'evenement outbox attendu. */
    @SuppressWarnings("unchecked")
    private OutboxEventBO stubCreateHappyPath(CreateDonationRequest request, DonationBO donation, DonationResponse response) {
        EventEnvelope<DonationCreatedEventPayload> envelope = mock(EventEnvelope.class);
        OutboxEventBO outboxEvent = mock(OutboxEventBO.class);
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(buildDonorBO()));
        when(donationMapper.toBo(request)).thenReturn(donation);
        when(donationRepository.save(donation)).thenReturn(donation);
        when(donationEventMapper.toDonationCreatedEnvelope(donation,
                new DonorSnapshot(DONOR_ID, DB_DATE_OF_BIRTH, DB_COUNTRY), PRODUCER)).thenReturn(envelope);
        when(outboxFactory.newEvent(TOPIC, DONATION_ID.toString(), envelope)).thenReturn(outboxEvent);
        when(donationMapper.toResponse(donation)).thenReturn(response);
        return outboxEvent;
    }

    @Test
    @DisplayName("create: saves donation and outbox event, returns response")
    void create_success() {
        CreateDonationRequest request = buildCreateRequest();
        DonationBO donation = buildDonationBO();
        DonationResponse expected = buildDonationResponse();
        OutboxEventBO outboxEvent = stubCreateHappyPath(request, donation, expected);

        assertThat(donationService.create(request)).isEqualTo(expected);
        verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("create: the event uses the donor snapshot from the database, not the one sent by the client")
    void create_eventUsesDonorFromDatabase() {
        CreateDonationRequest request = buildCreateRequest();
        DonationBO donation = buildDonationBO();
        stubCreateHappyPath(request, donation, buildDonationResponse());

        donationService.create(request);

        verify(donationEventMapper).toDonationCreatedEnvelope(donation,
                new DonorSnapshot(DONOR_ID, DB_DATE_OF_BIRTH, DB_COUNTRY), PRODUCER);
        verify(donationEventMapper, never()).toDonationCreatedEnvelope(any(), eq(request.donor()), any());
    }

    @Test
    @DisplayName("create: checks the donor, then saves the donation, then the outbox event")
    void create_writesInOrder() {
        CreateDonationRequest request = buildCreateRequest();
        DonationBO donation = buildDonationBO();
        OutboxEventBO outboxEvent = stubCreateHappyPath(request, donation, buildDonationResponse());

        donationService.create(request);

        var inOrder = inOrder(donorRepository, donationRepository, outboxEventRepository);
        inOrder.verify(donorRepository).findById(DONOR_ID);
        inOrder.verify(donationRepository).save(donation);
        inOrder.verify(outboxEventRepository).save(outboxEvent);
    }

    @Test
    @DisplayName("create: throws NotFoundException and writes nothing when donor does not exist")
    void create_donorNotFound() {
        CreateDonationRequest request = buildCreateRequest();
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.create(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(DONOR_ID.toString());
        verifyNoInteractions(donationMapper, donationEventMapper, outboxFactory);
        verify(donationRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    // ─── get ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: returns donation response when found")
    void get_found() {
        DonationBO donation = buildDonationBO();
        DonationResponse expected = buildDonationResponse();

        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.of(donation));
        when(donationMapper.toResponse(donation)).thenReturn(expected);

        assertThat(donationService.get(DONATION_ID)).isEqualTo(expected);
    }

    @Test
    @DisplayName("get: throws NotFoundException when donation does not exist")
    void get_notFound_throwsException() {
        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.get(DONATION_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(DONATION_ID.toString());
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: returns all donations as responses")
    void list_returnsAll() {
        DonationBO donation = buildDonationBO();
        DonationResponse response = buildDonationResponse();

        when(donationRepository.findAll()).thenReturn(List.of(donation));
        when(donationMapper.toResponse(donation)).thenReturn(response);

        assertThat(donationService.list()).containsExactly(response);
    }

    @Test
    @DisplayName("list: returns empty list when no donations exist")
    void list_empty() {
        when(donationRepository.findAll()).thenReturn(List.of());

        assertThat(donationService.list()).isEmpty();
    }

    // ─── listByDonor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("listByDonor: returns donations for the given donor")
    void listByDonor_returnsDonations() {
        DonationBO donation = buildDonationBO();
        DonationResponse response = buildDonationResponse();

        when(donationRepository.findByDonorId(DONOR_ID)).thenReturn(List.of(donation));
        when(donationMapper.toResponse(donation)).thenReturn(response);

        assertThat(donationService.listByDonor(DONOR_ID)).containsExactly(response);
    }

    @Test
    @DisplayName("listByDonor: returns empty list when donor has no donations")
    void listByDonor_empty() {
        when(donationRepository.findByDonorId(DONOR_ID)).thenReturn(List.of());

        assertThat(donationService.listByDonor(DONOR_ID)).isEmpty();
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: patches donation and returns updated response")
    void update_success() {
        DonationBO donation = buildDonationBO();
        var request = new UpdateDonationRequest(DonationCategory.FOOD, true, new BigDecimal("200.00"));
        DonationResponse expected = buildDonationResponse();

        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.of(donation));
        when(donationRepository.save(donation)).thenReturn(donation);
        when(donationMapper.toResponse(donation)).thenReturn(expected);

        assertThat(donationService.update(DONATION_ID, request)).isEqualTo(expected);
        verify(donationMapper).patch(donation, request);
    }

    @Test
    @DisplayName("update: throws NotFoundException when donation does not exist")
    void update_notFound_throwsException() {
        var request = new UpdateDonationRequest(null, null, null);
        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.update(DONATION_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: deletes donation when found")
    void delete_success() {
        DonationBO donation = buildDonationBO();
        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.of(donation));

        donationService.delete(DONATION_ID);

        verify(donationRepository).delete(donation);
    }

    @Test
    @DisplayName("delete: throws NotFoundException when donation does not exist")
    void delete_notFound_throwsException() {
        when(donationRepository.findById(DONATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donationService.delete(DONATION_ID))
                .isInstanceOf(NotFoundException.class);
    }
}