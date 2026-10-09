package ma.myc.inner.donation.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import ma.myc.inner.donation.config.properties.MycOpenApiProps;
import ma.myc.inner.donation.util.constants.GlobalConstants;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
public class OpenApiConfig {

	@Value ("${myc.context}")
	private String context;

	@Autowired
	private MycOpenApiProps openApiProps;

	private static final String DEFAULT_SERVER_DESCRIPTION = "Default Server";

	@Bean
	OpenAPI skeletonOpenAPI(@Value("${myc.docs.version}") String version) {
		return new OpenAPI()
				.info(apiInfo(version))
				.addServersItem(new Server().url(context).description(DEFAULT_SERVER_DESCRIPTION))
				.addSecurityItem(new SecurityRequirement().addList(openApiProps.getSchema()))
				.components(new Components().addSecuritySchemes(openApiProps.getSchema(), oauthSecurityScheme()));
	}

	private static Info apiInfo(String version) {
		return new Info().title(GlobalConstants.INFO_API_TITLE)
				.description(GlobalConstants.INFO_API_DESCRIPTION)
				.termsOfService(GlobalConstants.INFO_API_TERMS_OF_SERVICE)
				.version(version)
				.contact(contact());
	}

	private static Contact contact() {
		return new Contact()
				.name(GlobalConstants.CONTACT_NAME)
				.email(GlobalConstants.CONTACT_EMAIL)
				.url(GlobalConstants.CONTACT_WEBSITE);
	}

	private SecurityScheme oauthSecurityScheme() {
		OAuthFlows flows = oauthFlows();
		return new SecurityScheme()
				.name(openApiProps.getSchema())
				.flows(flows)
				.type(SecurityScheme.Type.OAUTH2);
	}

	private OAuthFlows oauthFlows() {
		return new OAuthFlows()
				.clientCredentials(clientCredentialsFlow());
	}

	private OAuthFlow clientCredentialsFlow() {
		return new OAuthFlow()
				.authorizationUrl(openApiProps.getAuthUri())
				.tokenUrl(openApiProps.getTokenUri())
				.scopes(new Scopes()
						.addString(GlobalConstants.SCOPE_DONATION_READ, "Lecture")
						.addString(GlobalConstants.SCOPE_DONATION_WRITE, "Creation, modification, suppression"));
	}

}
