package com.eventhq.ticket;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {
  private final TicketService service;

  @GetMapping
  public List<TicketService.TicketView> tickets(@AuthenticationPrincipal Jwt jwt) {
    return service.owned(Long.valueOf(jwt.getSubject()));
  }
}
