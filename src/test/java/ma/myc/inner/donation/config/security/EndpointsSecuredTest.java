package ma.myc.inner.donation.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou (refus par defaut) : toute methode d'endpoint d'un controleur de l'API doit porter un @PreAuthorize.
 * Un nouvel endpoint sans permission fait echouer le build.
 */
class EndpointsSecuredTest {

    private static final String API_PACKAGE = "ma.myc.inner.donation.api";

    @Test
    @DisplayName("every endpoint method of every REST controller declares a @PreAuthorize permission")
    void everyEndpoint_hasPreAuthorize() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<Method> endpoints = scanner.findCandidateComponents(API_PACKAGE).stream()
                .map(bean -> ClassUtils.resolveClassName(bean.getBeanClassName(), getClass().getClassLoader()))
                .flatMap(controller -> Arrays.stream(controller.getDeclaredMethods()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();

        assertThat(endpoints).as("endpoints scanned in " + API_PACKAGE).hasSizeGreaterThanOrEqualTo(11);
        assertThat(endpoints)
                .filteredOn(method -> !AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class))
                .as("endpoints without @PreAuthorize")
                .isEmpty();
    }
}
