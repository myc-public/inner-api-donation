package ma.myc.inner.donation.config.properties;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Configuration
@ConfigurationProperties(prefix = "myc.security")
public class MycSecurityProps {
	private boolean enabled;
	// Aucune origine par defaut : CORS ferme (cf. SecurityConfig.corsFilter)
	private String allowedOriginPattern;
	private List<String> whitelistPath = Collections.emptyList();
	private MycCspProps csp = new MycCspProps();

	@Data
	@ToString
	@EqualsAndHashCode
	public static class MycCspProps {
		private boolean enabled;
		private String directives = "default-src 'none'";
	}

}
