package com.bank.balancedashboard.unit;

import com.bank.balancedashboard.infrastructure.rest.CustomerIdResolver;
import com.bank.balancedashboard.infrastructure.rest.InvalidJwtSubException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CustomerIdResolver}.
 * ZERO Spring context — pure unit test (resolver takes Jwt argument, not HttpServletRequest).
 * Coverage target: >= 95% (security critical path per task-plan Layer 6).
 */
class CustomerIdResolverTest {

    private CustomerIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CustomerIdResolver();
    }

    @Test
    @DisplayName("(1) Valid UUID sub -> returns UUID")
    void validUuidSub_returnsUuid() {
        UUID expectedId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        Jwt jwt = buildJwt(expectedId.toString());

        UUID result = resolver.resolve(jwt);

        assertThat(result).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("(2) null sub -> throws InvalidJwtSubException")
    void nullSub_throwsInvalidJwtSubException() {
        Jwt jwt = buildJwt(null);

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(InvalidJwtSubException.class)
                .hasMessageContaining("missing");
    }

    @Test
    @DisplayName("(3) Non-UUID sub -> throws InvalidJwtSubException")
    void nonUuidSub_throwsInvalidJwtSubException() {
        Jwt jwt = buildJwt("not-a-uuid");

        assertThatThrownBy(() -> resolver.resolve(jwt))
                .isInstanceOf(InvalidJwtSubException.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    @DisplayName("(4) No Spring context needed — pure unit test")
    void noSpringContextNeeded_pureUnitTest() {
        // Verifies the resolver is a plain POJO — no Spring injection required
        CustomerIdResolver standaloneResolver = new CustomerIdResolver();
        UUID id = UUID.randomUUID();
        Jwt jwt = buildJwt(id.toString());

        assertThat(standaloneResolver.resolve(jwt)).isEqualTo(id);
    }

    // ===== Helpers =====

    private Jwt buildJwt(String subject) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = subject != null
                ? Map.of("sub", subject, "scope", "accounts:read")
                : Map.of("scope", "accounts:read");

        return Jwt.withTokenValue("test-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
