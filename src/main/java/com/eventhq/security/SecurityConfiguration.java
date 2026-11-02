package com.eventhq.security;

import com.eventhq.config.EventHqProperties;
import com.eventhq.exception.ErrorResponses;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
  @Bean
  SecretKey jwtKey(EventHqProperties p) {
    return new SecretKeySpec(p.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  JwtEncoder jwtEncoder(SecretKey k) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(k));
  }

  @Bean
  JwtDecoder jwtDecoder(SecretKey k) {
    NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(k).macAlgorithm(MacAlgorithm.HS256).build();
    d.setJwtValidator(JwtValidators.createDefaultWithIssuer("eventhq"));
    return d;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity h, ObjectMapper m, Environment env) throws Exception {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("roles");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    org.springframework.security.web.AuthenticationEntryPoint entry =
        (r, s, e) -> {
          s.setStatus(401);
          s.setContentType("application/json");
          m.writeValue(
              s.getOutputStream(),
              ErrorResponses.body(401, "UNAUTHENTICATED", "Authentication required", r, Map.of()));
        };
    org.springframework.security.web.access.AccessDeniedHandler denied =
        (r, s, e) -> {
          s.setStatus(403);
          s.setContentType("application/json");
          m.writeValue(
              s.getOutputStream(),
              ErrorResponses.body(403, "FORBIDDEN", "Access denied", r, Map.of()));
        };
    h.csrf(c -> c.disable())
        .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(c -> c.disable())
        .httpBasic(c -> c.disable());
    h.authorizeHttpRequests(
        a -> {
          a.requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/index.html", "/assets/**").permitAll();
          a.requestMatchers(org.springframework.http.HttpMethod.HEAD, "/", "/index.html", "/assets/**").permitAll();
          a.requestMatchers("/api/v1/auth/**", "/actuator/health", "/actuator/health/**")
              .permitAll();
          if (env.matchesProfiles("dev")) a.requestMatchers("/h2-console/**").hasRole("ADMIN");
          a.requestMatchers("/api/v1/admin/**", "/admin/**", "/actuator/**").hasRole("ADMIN");
          a.requestMatchers(
                  org.springframework.http.HttpMethod.GET, "/api/v1/events/**", "/api/v1/venues/**")
              .permitAll();
          a.anyRequest().authenticated();
        });
    h.headers(c -> c.frameOptions(f -> f.sameOrigin()));
    h.exceptionHandling(c -> c.authenticationEntryPoint(entry).accessDeniedHandler(denied));
    h.oauth2ResourceServer(
        c ->
            c.jwt(j -> j.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entry)
                .accessDeniedHandler(denied));
    return h.build();
  }
}
