package com.eventhq.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtTest {
  @Test
  void expiredWrongIssuerAndTamperedTokensAreRejected() {
    var key =
        new SecretKeySpec(
            "test-key-with-at-least-32-random-bytes-1234".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    var config = new SecurityConfiguration();
    var encoder = config.jwtEncoder(key);
    var decoder = config.jwtDecoder(key);
    var now = Instant.now();
    var expired =
        JwtClaimsSet.builder()
            .issuer("eventhq")
            .subject("1")
            .issuedAt(now.minusSeconds(3600))
            .expiresAt(now.minusSeconds(120))
            .build();
    assertThatThrownBy(() -> decoder.decode(encode(encoder, expired)))
        .isInstanceOf(JwtException.class);
    var wrong =
        JwtClaimsSet.builder()
            .issuer("other")
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    assertThatThrownBy(() -> decoder.decode(encode(encoder, wrong)))
        .isInstanceOf(JwtException.class);
    var valid =
        JwtClaimsSet.builder()
            .issuer("eventhq")
            .subject("1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(600))
            .build();
    String token = encode(encoder, valid);
    assertThat(decoder.decode(token).getSubject()).isEqualTo("1");
    String[] parts = token.split("\\.");
    parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
    assertThatThrownBy(() -> decoder.decode(String.join(".", parts)))
        .isInstanceOf(JwtException.class);
  }

  private String encode(JwtEncoder encoder, JwtClaimsSet claims) {
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
