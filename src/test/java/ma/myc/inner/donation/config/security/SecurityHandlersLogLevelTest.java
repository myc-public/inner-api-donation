package ma.myc.inner.donation.config.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ma.myc.inner.donation.util.component.TraceRequestHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Refus levés par la chaîne de sécurité (avant le contrôleur) : erreurs client (401 / 403) journalisées en WARN,
 * jamais en ERROR (alerte A5 "logs ERROR"). Recette OpenShift du 09/10 : chaque requête sans token déclenchait A5.
 * Pendant de ErrorHandlingAdviceLogLevelTest pour les refus levés par les contrôleurs.
 */
class SecurityHandlersLogLevelTest {

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/donors");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private Logger logger;

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
    }

    @Test
    @DisplayName("401 de la chaîne de sécurité (token absent) : WARN")
    void authEntryPoint_logsWarn() throws Exception {
        capture(SecurityAuthEntryPoint.class);
        new SecurityAuthEntryPoint(traceRequestHandler(), JsonMapper.builder().build())
                .commence(request, response, new InsufficientAuthenticationException("token absent"));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    @Test
    @DisplayName("403 de la chaîne de sécurité (scope insuffisant) : WARN")
    void accessDeniedHandler_logsWarn() throws Exception {
        capture(SecurityAccessDeniedHandler.class);
        new SecurityAccessDeniedHandler(traceRequestHandler(), JsonMapper.builder().build())
                .handle(request, response, new AccessDeniedException("refus"));
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    private void capture(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        logs.start();
        logger.addAppender(logs);
    }

    private static TraceRequestHandler traceRequestHandler() {
        TraceRequestHandler traceRequestHandler = mock(TraceRequestHandler.class);
        when(traceRequestHandler.getCorrelationId()).thenReturn("trace-test");
        return traceRequestHandler;
    }
}
