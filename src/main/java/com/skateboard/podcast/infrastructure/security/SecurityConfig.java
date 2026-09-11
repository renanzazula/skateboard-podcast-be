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
 * Configures this service as an OAuth2 Resource Server for access tokens
 * issued by Keycloak.
 *
 * JWT signatures are verified using the realm's JWKS endpoint. In addition
 * to the standard issuer and timestamp validation, {@link AudienceValidator}
 * requires the token's {@code aud} claim to contain this service's audience,
 * preventing tokens intended for another API from being accepted.
 * <p>
 * Authorities are read verbatim from the {@code authorities} claim without
 * Spring's default {@code ROLE_} or {@code SCOPE_} prefixes. The Keycloak
 * {@code authorities} protocol mapper, defined in {@code realm-export.json},
 * populates this claim with the user's effective {@code FUNC_*} permissions.
 * This allows controller authorization such as:
 *
 * <pre>
 * {@code @PreAuthorize("hasAuthority('FUNC_PODCAST_CREATE')")}
 * </pre>
 *
 * The JWKS URI is derived directly from the Keycloak issuer URI using
 * Keycloak's {@code /protocol/openid-connect/certs} endpoint instead of
 * relying on issuer-based OIDC discovery.
 * <p>
 * Avoiding discovery during decoder construction removes a synchronous
 * dependency on Keycloak during application startup. JWKS retrieval occurs
 * when token decoding requires the signing keys.
 * <p>
 * This also keeps controller security tests independent from a running
 * Keycloak instance: Spring Security Test's {@code jwt()} request
 * post-processor can provide a test {@link Jwt} directly.
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
                // Safe to disable: this API is a stateless OAuth2 resource server
                // (SessionCreationPolicy.STATELESS below) authenticated by a bearer
                // JWT on every request, never by a session cookie — there is no
                // ambient credential for a cross-site request to ride on.
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
