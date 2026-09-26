package ma.myc.inner.donation.config;

import ma.myc.inner.donation.api.DonorController;
import ma.myc.inner.donation.domain.dto.DonorResponse;
import ma.myc.inner.donation.domain.dto.UpdateDonorRequest;
import ma.myc.inner.donation.exception.DonorAlreadyExistsException;
import ma.myc.inner.donation.exception.NotFoundException;
import ma.myc.inner.donation.service.DonorService;
import ma.myc.inner.donation.util.constants.ErrorConstants;
import ma.myc.inner.donation.util.constants.GlobalConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(DonorController.class)
@Import(TestConfig.class)
@ExtendWith(SpringExtension.class)
class ErrorHandlingAdviceTest {

    private static final String DONORS = "/api/v1/donors";
    private static final String EMAIL = "jean.molin@example.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DonorService donorService;

    @Test
    @WithMockUser(authorities = {GlobalConstants.SCOPE})
    @DisplayName("GET /donors/{id} returns 404 when donor does not exist")
    void get_unknownDonor_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(donorService.get(any(UUID.class)))
                .thenThrow(new NotFoundException("Donor not found: " + unknownId));

        mockMvc.perform(get(DONORS + "/{id}", unknownId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_NOT_FOUND)));
    }

    @Test
    @DisplayName("GET /donors/{id} returns 400 (not 500) when the id is not a valid UUID")
    void get_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(DONORS + "/{id}", "not-a-uuid")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_ARGUMENT_TYPE_MISMATCH)))
                .andExpect(jsonPath("$.errors[0].target", is("donorId")))
                .andExpect(jsonPath("$.errors[0].message", is("must be a valid UUID")));

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("PATCH /donors/{id} returns 409 without the email when the new email is already used")
    void update_emailAlreadyUsed_returns409WithoutEmail() throws Exception {
        UUID donorId = UUID.randomUUID();
        when(donorService.update(eq(donorId), any(UpdateDonorRequest.class)))
                .thenThrow(new DonorAlreadyExistsException());

        mockMvc.perform(patch(DONORS + "/{id}", donorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_CONFLICT)))
                .andExpect(jsonPath("$.detail", is(DonorAlreadyExistsException.MESSAGE)))
                .andExpect(content().string(not(containsString(EMAIL))));
    }

    @Test
    @DisplayName("Database constraint violation returns 409 without the SQL message")
    void dataIntegrityViolation_returns409WithoutSqlDetail() throws Exception {
        UUID donorId = UUID.randomUUID();
        when(donorService.update(eq(donorId), any(UpdateDonorRequest.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Duplicate entry '" + EMAIL + "' for key 'donor.uk_donor_email'"));

        mockMvc.perform(patch(DONORS + "/{id}", donorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"Molin\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_CONFLICT)))
                .andExpect(content().string(not(containsString(EMAIL))))
                .andExpect(content().string(not(containsString("uk_donor_email"))));
    }

    @Test
    @DisplayName("POST /donors returns 415 when the body is not JSON")
    void create_unsupportedContentType_returns415() throws Exception {
        mockMvc.perform(post(DONORS)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("lastName=Molin"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_UNSUPPORTED_MEDIA_TYPE)));

        verifyNoInteractions(donorService);
    }

    @Test
    @DisplayName("GET /donors/{id} returns 406 when the client does not accept JSON")
    void get_notAcceptable_returns406() throws Exception {
        UUID donorId = UUID.randomUUID();
        when(donorService.get(donorId)).thenReturn(
                new DonorResponse(donorId, "Molin", "Jean", EMAIL, LocalDate.of(1999, 9, 19), "MA"));

        mockMvc.perform(get(DONORS + "/{id}", donorId)
                        .accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    @DisplayName("POST /donors returns 400 with a generic detail when the JSON is malformed")
    void create_malformedJson_returns400WithoutParserDetail() throws Exception {
        mockMvc.perform(post(DONORS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\", \"dateOfBirth\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_HTTP_MESSAGE_NOT_READABLE)))
                .andExpect(content().string(not(containsString(EMAIL))))
                .andExpect(content().string(not(containsString("not-a-date"))));
    }

    @Test
    @DisplayName("POST /donors returns 400 listing invalid fields without echoing rejected values")
    void create_invalidBody_returns400WithoutRejectedValues() throws Exception {
        mockMvc.perform(post(DONORS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"Molin\",\"firstName\":\"Jean\",\"email\":\"jean.molin-at-example\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", is(ErrorConstants.URI_METHOD_ARGUMENT_NOT_VALID)))
                .andExpect(jsonPath("$.errors[0].target", is("email")))
                .andExpect(content().string(not(containsString("jean.molin-at-example"))));
    }
}
