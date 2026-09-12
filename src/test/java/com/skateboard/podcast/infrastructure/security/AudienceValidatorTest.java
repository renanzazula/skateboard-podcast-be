package com.skateboard.podcast.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keycloak issues tokens for many clients in the realm; without this check
 * any valid token from the realm — regardless of which client it was minted
 * for — would authenticate here (see CLAUDE.md, "Auth model").
 */
class AudienceValidatorTest {

    private static final String REQUIRED_AUDIENCE = "skateboard-podcast-be";

    private final AudienceValidator validator = new AudienceValidator(REQUIRED_AUDIENCE);

    @Test
    void acceptsATokenThatListsTheRequiredAudience() {
        Jwt jwt = jwtWithAudience(List.of(REQUIRED_AUDIENCE));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void acceptsATokenListingSeveralAudiencesIncludingTheRequiredOne() {
        Jwt jwt = jwtWithAudience(List.of("some-other-client", REQUIRED_AUDIENCE));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenMintedForADifferentClient() {
        Jwt jwt = jwtWithAudience(List.of("skateboard-app-config-be"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    /**
     * A token with no "aud" claim at all comes back from Jwt#getAudience() as
     * null (not an empty list) — this must be rejected cleanly, not blow up
     * with an NPE.
     */
    @Test
    void rejectsATokenWithNoAudienceClaimWithoutThrowing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertThat(jwt.getAudience()).isNull();

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    private Jwt jwtWithAudience(List<String> audience) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .audience(audience)
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
