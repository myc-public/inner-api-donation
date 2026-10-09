package ma.myc.inner.donation.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.ConstraintViolationException;
import ma.myc.inner.donation.util.component.MsgSource;
import ma.myc.inner.donation.util.component.TraceRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Niveaux de log : une erreur client (4xx) est attendue en exploitation (refus RBAC / ABAC, requete mal formee)
 * et se journalise en WARN ; seule une erreur serveur (5xx) se journalise en ERROR et declenche l'alerte
 * "logs ERROR" (A5). Recette OpenShift du 09/10 : chaque 403 legitime declenchait A5.
 */
class ErrorHandlingAdviceLogLevelTest {

    private final Logger adviceLogger = (Logger) LoggerFactory.getLogger(ErrorHandlingAdvice.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/donors");
    private ErrorHandlingAdvice advice;

    @BeforeEach
    void setUp() {
        TraceRequestHandler traceRequestHandler = mock(TraceRequestHandler.class);
        when(traceRequestHandler.getCorrelationId()).thenReturn("trace-test");
        advice = new ErrorHandlingAdvice(mock(MsgSource.class), traceRequestHandler);
        logs.start();
        adviceLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        adviceLogger.detachAppender(logs);
    }

    @Test
    @DisplayName("403 acces refuse : WARN")
    void accessDenied_logsWarn() {
        advice.onAccessDeniedException(new AccessDeniedException("refus"), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("401 authentification : WARN")
    void authentication_logsWarn() {
        advice.onAuthenticationException(new BadCredentialsException("token invalide"), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("400 parametre manquant : WARN")
    void missingParameter_logsWarn() {
        advice.onMissingServletRequestParameterException(new MissingServletRequestParameterException("id", "String"), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("400 partie manquante : WARN")
    void missingPart_logsWarn() {
        advice.onMissingServletRequestPartException(new MissingServletRequestPartException("fichier"), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("405 methode non supportee : WARN")
    void methodNotSupported_logsWarn() {
        advice.onHttpRequestMethodNotSupportedException(new HttpRequestMethodNotSupportedException("TRACE"), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("400 contrainte de validation : WARN")
    void constraintViolation_logsWarn() {
        advice.onConstraintValidationException(new ConstraintViolationException("invalide", Set.of()), request);
        assertSingleLevel(Level.WARN);
    }

    @Test
    @DisplayName("500 erreur technique : ERROR (doit declencher l'alerte)")
    void technical_logsError() {
        advice.onTechnicalException(new RuntimeException("panne"), request);
        assertSingleLevel(Level.ERROR);
    }

    private void assertSingleLevel(Level expected) {
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(expected);
    }
}
