package ma.myc.inner.donation.config.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import lombok.RequiredArgsConstructor;
import ma.myc.inner.donation.config.properties.MycSecurityProps;
import ma.myc.inner.donation.util.constants.GlobalConstants;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!test")
public class SecurityConfig {
	private static final int ORDER = 2;

	@Configuration
	@Profile("!test")
	@RequiredArgsConstructor
	public static class SecurityFilterChainConfig {
		private final SecurityAuthEntryPoint securityAuthEntryPoint;
		private final SecurityAccessDeniedHandler securityAccessDeniedHandler;
		private final MycSecurityProps securityProps;

		@Order(ORDER)
		@Bean
		// Securite active par defaut : sans myc.security.enabled, seule la chaine securisee est creee
		// (avant : les deux chaines l'etaient, et le demarrage echouait sur deux chaines "any request")
		@ConditionalOnProperty(prefix = "myc.security", name = "enabled", havingValue = "false")
		SecurityFilterChain disabledSecurityFilterChain(HttpSecurity http) throws Exception {
			http
					.authorizeHttpRequests(t -> t.anyRequest().permitAll());
			http
					.formLogin(FormLoginConfigurer::disable)
					.logout(LogoutConfigurer::disable)
					.cors(withDefaults())
					.csrf(CsrfConfigurer::disable) // NOSONAR
					.headers(h -> h.frameOptions(FrameOptionsConfig::disable))
					.exceptionHandling(handling -> handling
							.accessDeniedHandler(securityAccessDeniedHandler)
							.authenticationEntryPoint(securityAuthEntryPoint));

			return http.build();
		}

		@Order(ORDER)
		@Bean
		@ConditionalOnProperty(prefix = "myc.security", name = "enabled", havingValue = "true", matchIfMissing = true)
		SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			http.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
					.authorizeHttpRequests(authRequests -> authRequests
							.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
							.requestMatchers(GlobalConstants.API_V1_PATTERN).hasAuthority(GlobalConstants.SCOPE)
							.requestMatchers(securityProps.getWhitelistPath().toArray(String[]::new)).permitAll()
							.anyRequest().authenticated())
					.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
							.authenticationEntryPoint(securityAuthEntryPoint)
							.accessDeniedHandler(securityAccessDeniedHandler))
					.formLogin(FormLoginConfigurer::disable)
					.logout(LogoutConfigurer::disable)
					.cors(withDefaults())
					.csrf(CsrfConfigurer::disable) // NOSONAR
					.headers(h -> h.frameOptions(FrameOptionsConfig::disable))
					.exceptionHandling(handling -> handling
							.accessDeniedHandler(securityAccessDeniedHandler)
							.authenticationEntryPoint(securityAuthEntryPoint));
			if (securityProps.getCsp().isEnabled())
				http.headers(h -> h.contentSecurityPolicy(t -> t.policyDirectives(securityProps.getCsp()
						.getDirectives())));

			return http.build();
		}

		@Bean
		CorsFilter corsFilter() {
			var source = new UrlBasedCorsConfigurationSource();
			var config = new CorsConfiguration();
			config.setAllowCredentials(true);
			config.addAllowedOriginPattern(securityProps.getAllowedOriginPattern());
			config.addAllowedHeader("*");
			config.addAllowedMethod("*");
			source.registerCorsConfiguration("/**", config);
			return new CorsFilter(source);
		}
	}
}
