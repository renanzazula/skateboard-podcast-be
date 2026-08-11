package com.skateboard.podcast.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 resource server validating access tokens issued by Keycloak: RS256,
 * verified against the realm's JWKS endpoint, restricted to tokens whose
 * "aud" includes this service (see {@link AudienceValidator}). Authorities are
 * read verbatim (no ROLE_/SCOPE_ prefix) from the "authorities" claim, which
 * the realm's "authorities" protocol mapper (realm-export.json) populates
 * with the user's effective FUNC_* roles — so
 * {@code @PreAuthorize("hasAuthority('FUNC_...')")} checks on the controllers
 * work unmodified.
 * <p>
 * The JWKS URI is built directly from {@code issuerUri} (Keycloak's stable
 * {@code /protocol/openid-connect/certs} convention) instead of doing OIDC
 * discovery ({@code JwtDecoders.fromIssuerLocation}): discovery makes a
 * blocking HTTP call while this bean is constructed, coupling app startup to
 * Keycloak being reachable at that exact moment. Building the JWKS URI
 * directly keeps key fetching lazy (first token verification), which is both
 * more resilient at boot and lets tests substitute a fake JWT
 * (spring-security-test's {@code jwt()} request post-processor) without
 * needing a real Keycloak reachable at all.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final String issuerUri;
    private final String requiredAudience;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
                           @Value("${app.security.oauth2.audience}") String requiredAudience) {
        this.issuerUri = issuerUri;
        this.requiredAudience = requiredAudience;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuerUri + "/protocol/openid-connect/certs").build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(requiredAudience));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("authorities");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
