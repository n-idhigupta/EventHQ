package com.eventhq.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService service;

  public record Credentials(
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank
          @Size(min = 12, max = 72)
          @Pattern(regexp = "[ -~]+", message = "must contain ASCII printable characters")
          String password) {}

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthService.Token register(@Valid @RequestBody Credentials r) {
    return service.register(r.email(), r.password());
  }

  @PostMapping("/login")
  public AuthService.Token login(@Valid @RequestBody Credentials r) {
    return service.login(r.email(), r.password());
  }
}
