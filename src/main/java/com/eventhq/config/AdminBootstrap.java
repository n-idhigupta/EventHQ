package com.eventhq.config;

import com.eventhq.user.*;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {
  private final EventHqProperties p;
  private final UserRepository users;
  private final PasswordEncoder encoder;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    String email = p.adminEmail(), password = p.adminPassword();
    if (email == null || email.isBlank()) {
      if (password != null && !password.isBlank())
        throw new IllegalStateException("ADMIN_EMAIL is required with ADMIN_PASSWORD");
      return;
    }
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalStateException(
          "ADMIN_PASSWORD must have at least 12 characters and at most 72 UTF-8 bytes");
    String n = email.strip().toLowerCase(Locale.ROOT);
    var existing = users.findByEmail(n);
    if (existing.isPresent()) {
      if (!"ADMIN".equals(existing.get().getRole()))
        throw new IllegalStateException("Bootstrap email belongs to a customer");
      return;
    }
    User u = new User();
    u.setEmail(n);
    u.setPasswordHash(encoder.encode(password));
    u.setRole("ADMIN");
    users.save(u);
  }
}
