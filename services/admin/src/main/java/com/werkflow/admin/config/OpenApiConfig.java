package com.werkflow.admin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI adminServiceOpenAPI() {
        final String bearerSchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("Werkflow Admin Service API")
                .description("User, Organization, and Role Management Service. Click \"Authorize\" to login and obtain a token for testing.")
                .version("1.0.0")
                .contact(new Contact()
                    .name("Werkflow Team")
                    .email("admin@werkflow.com"))
                .license(new License()
                    .name("Proprietary")
                    .url("https://werkflow.com/license")))
            .addSecurityItem(new SecurityRequirement().addList("oauth2"))
            .addSecurityItem(new SecurityRequirement().addList(bearerSchemeName))
            .components(new Components()
                .addSecuritySchemes("oauth2", new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                            .authorizationUrl("http://localhost:8090/realms/werkflow/protocol/openid-connect/auth")
                            .tokenUrl("http://localhost:8090/realms/werkflow/protocol/openid-connect/token")
                            .refreshUrl("http://localhost:8090/realms/werkflow/protocol/openid-connect/token")
                            .scopes(new Scopes()
                                .addString("openid", "OpenID Connect")
                                .addString("profile", "User profile")
                                .addString("email", "Email address")))))
                .addSecuritySchemes(bearerSchemeName, new SecurityScheme()
                    .name(bearerSchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT token obtained from Keycloak")));
    }
}
