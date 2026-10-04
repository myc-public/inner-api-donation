package ma.myc.inner.donation.service;

import ma.myc.inner.donation.domain.bo.DonorBO;
import ma.myc.inner.donation.domain.dto.CreateDonorRequest;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.RegisterDonorRequest;
import ma.myc.inner.donation.domain.dto.UpdateDonorRequest;
import ma.myc.inner.donation.exception.DonorAlreadyExistsException;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.mapper.DonorMapper;
import ma.myc.inner.donation.repository.DonorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonorServiceImplTest {

    @Mock
    private DonorRepository donorRepository;

    @Mock
    private DonorMapper donorMapper;

    @InjectMocks
    private DonorServiceImpl donorService;

    private static final UUID DONOR_ID = UUID.randomUUID();
    private static final String EMAIL = "john.doe@example.com";
    private static final Instant NOW = Instant.now();

    private DonorBO buildDonorBO() {
        return new DonorBO(DONOR_ID, "Doe", "John", EMAIL,
                LocalDate.of(1990, 1, 1), "MA", NOW);
    }

    private DonorResponse buildDonorResponse() {
        return new DonorResponse(DONOR_ID, "Doe", "John", EMAIL,
                LocalDate.of(1990, 1, 1), "MA");
    }

    // ─── create ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: saves donor and returns response when email is unique")
    void create_success() {
        var request = new CreateDonorRequest("Doe", "John", EMAIL, LocalDate.of(1990, 1, 1), "MA");
        DonorBO donor = buildDonorBO();
        DonorResponse expected = buildDonorResponse();

        when(donorRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(donorMapper.toBo(request)).thenReturn(donor);
        when(donorRepository.save(donor)).thenReturn(donor);
        when(donorMapper.toResponse(donor)).thenReturn(expected);

        assertThat(donorService.create(request)).isEqualTo(expected);
        verify(donorRepository).save(donor);
    }

    @Test
    @DisplayName("create: throws DonorAlreadyExistsException when email is already registered")
    void create_emailAlreadyExists_throwsException() {
        var request = new CreateDonorRequest("Doe", "John", EMAIL, LocalDate.of(1990, 1, 1), "MA");

        when(donorRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> donorService.create(request))
                .isInstanceOf(DonorAlreadyExistsException.class)
                .hasMessage(DonorAlreadyExistsException.MESSAGE)
                .message().doesNotContain(EMAIL);

        verify(donorRepository, never()).save(any());
    }

    // ─── get ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: returns donor response when donor exists")
    void get_found() {
        DonorBO donor = buildDonorBO();
        DonorResponse expected = buildDonorResponse();

        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(donor));
        when(donorMapper.toResponse(donor)).thenReturn(expected);

        assertThat(donorService.get(DONOR_ID)).isEqualTo(expected);
    }

    @Test
    @DisplayName("get: throws NotFoundException when donor does not exist")
    void get_notFound_throwsException() {
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donorService.get(DONOR_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(DONOR_ID.toString());
    }

    // ─── list ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: returns page of donor responses")
    void list_returnsPage() {
        DonorBO donor = buildDonorBO();
        DonorResponse response = buildDonorResponse();
        Pageable pageable = PageRequest.of(0, 20);

        when(donorRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(donor), pageable, 1));
        when(donorMapper.toResponse(donor)).thenReturn(response);

        Page<DonorResponse> result = donorService.list(pageable);

        assertThat(result.getContent()).containsExactly(response);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("list: returns empty page when no donors exist")
    void list_empty() {
        Pageable pageable = PageRequest.of(0, 20);
        when(donorRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        assertThat(donorService.list(pageable).getContent()).isEmpty();
    }

    // ─── update ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("update: patches donor and saves when email is unchanged")
    void update_sameEmail_success() {
        DonorBO donor = buildDonorBO();
        var request = new UpdateDonorRequest("Smith", null, EMAIL, null, null);
        DonorResponse expected = buildDonorResponse();

        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(donor));
        when(donorRepository.save(donor)).thenReturn(donor);
        when(donorMapper.toResponse(donor)).thenReturn(expected);

        assertThat(donorService.update(DONOR_ID, request)).isEqualTo(expected);
        verify(donorMapper).patch(donor, request);
        verify(donorRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("update: patches donor and saves when new email is not taken")
    void update_newEmailAvailable_success() {
        DonorBO donor = buildDonorBO();
        var request = new UpdateDonorRequest(null, null, "new@example.com", null, null);
        DonorResponse expected = buildDonorResponse();

        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(donor));
        when(donorRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(donorRepository.save(donor)).thenReturn(donor);
        when(donorMapper.toResponse(donor)).thenReturn(expected);

        assertThat(donorService.update(DONOR_ID, request)).isEqualTo(expected);
        verify(donorMapper).patch(donor, request);
    }

    @Test
    @DisplayName("update: throws DonorAlreadyExistsException when new email is already taken by another donor")
    void update_emailAlreadyTaken_throwsException() {
        DonorBO donor = buildDonorBO();
        var request = new UpdateDonorRequest(null, null, "taken@example.com", null, null);

        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(donor));
        when(donorRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> donorService.update(DONOR_ID, request))
                .isInstanceOf(DonorAlreadyExistsException.class)
                .message().doesNotContain("taken@example.com");

        verify(donorRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: throws NotFoundException when donor does not exist")
    void update_notFound_throwsException() {
        var request = new UpdateDonorRequest(null, null, null, null, null);
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donorService.update(DONOR_ID, request))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: deletes donor when found")
    void delete_success() {
        DonorBO donor = buildDonorBO();
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.of(donor));

        donorService.delete(DONOR_ID);

        verify(donorRepository).delete(donor);
    }

    @Test
    @DisplayName("delete: throws NotFoundException when donor does not exist")
    void delete_notFound_throwsException() {
        when(donorRepository.findById(DONOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> donorService.delete(DONOR_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── registerSelf (K4c) ───────────────────────────────────────────────────

    @Test
    @DisplayName("registerSelf: creates the profile with the given id (party_id) and the token email, never a generated id")
    void registerSelf_success() {
        var request = new RegisterDonorRequest("Doe", "John", LocalDate.of(1990, 1, 1), "MA");
        when(donorRepository.existsById(DONOR_ID)).thenReturn(false);
        when(donorRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(donorRepository.save(any(DonorBO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(donorMapper.toResponse(any(DonorBO.class))).thenReturn(buildDonorResponse());

        donorService.registerSelf(DONOR_ID, EMAIL, request);

        ArgumentCaptor<DonorBO> saved = ArgumentCaptor.forClass(DonorBO.class);
        verify(donorRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(DONOR_ID);
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        verify(donorMapper, never()).toBo(any());
    }

    @Test
    @DisplayName("registerSelf: a second profile for the same person is a conflict")
    void registerSelf_profileExists_conflict() {
        when(donorRepository.existsById(DONOR_ID)).thenReturn(true);

        assertThatThrownBy(() -> donorService.registerSelf(DONOR_ID, EMAIL,
                new RegisterDonorRequest("Doe", "John", null, "MA")))
                .isInstanceOf(DonorAlreadyExistsException.class)
                .hasMessage(DonorAlreadyExistsException.PROFILE_MESSAGE);
        verify(donorRepository, never()).save(any());
    }

    @Test
    @DisplayName("registerSelf: an email already used by another donor is a conflict")
    void registerSelf_emailTaken_conflict() {
        when(donorRepository.existsById(DONOR_ID)).thenReturn(false);
        when(donorRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> donorService.registerSelf(DONOR_ID, EMAIL,
                new RegisterDonorRequest("Doe", "John", null, "MA")))
                .isInstanceOf(DonorAlreadyExistsException.class)
                .hasMessage(DonorAlreadyExistsException.MESSAGE);
        verify(donorRepository, never()).save(any());
    }
}
