package com.eventhq.auth;

import com.eventhq.config.EventHqProperties;
import com.eventhq.exception.ApiException;
import com.eventhq.user.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final JwtEncoder encoder;
  private final Clock clock;
  private final EventHqProperties properties;

  public record Token(String accessToken, String tokenType, long expiresIn) {}

  @Transactional
  public Token register(String email, String password) {
    String n = normalize(email);
    if (users.findByEmail(n).isPresent()) throw ApiException.conflict("Email already registered");
    User u = new User();
    u.setEmail(n);
    u.setPasswordHash(passwords.encode(password));
    u.setRole("CUSTOMER");
    return token(users.saveAndFlush(u));
  }

  @Transactional(readOnly = true)
  public Token login(String email, String password) {
    User u =
        users
            .findByEmail(normalize(email))
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    if (!passwords.matches(password, u.getPasswordHash()))
      throw new BadCredentialsException("Invalid credentials");
    return token(u);
  }

  private Token token(User u) {
    var now = clock.instant();
    var claims =
        JwtClaimsSet.builder()
            .issuer("eventhq")
            .subject(u.getId().toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.jwtLifetime()))
            .claim("roles", List.of(u.getRole()))
            .build();
    return new Token(
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue(),
        "Bearer",
        properties.jwtLifetime().toSeconds());
  }

  private String normalize(String s) {
    return s.strip().toLowerCase(Locale.ROOT);
  }
}
