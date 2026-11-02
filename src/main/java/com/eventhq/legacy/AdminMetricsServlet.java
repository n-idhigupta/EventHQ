package com.eventhq.legacy;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class AdminMetricsServlet extends HttpServlet {
  @Autowired private MeterRegistry registry;
  @Autowired private Environment environment;

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("text/html;charset=UTF-8");
    resp.setHeader("Cache-Control", "no-store");
    String app =
        HtmlUtils.htmlEscape(environment.getProperty("spring.application.name", "EventHQ"));
    resp.getWriter()
        .printf(
            "<!doctype html><html lang='en'><head><meta charset='utf-8'><title>%s"
                + " metrics</title></head><body><h1>%s health metrics</h1><dl><dt>Uptime"
                + " milliseconds</dt><dd>%d</dd><dt>Registered"
                + " meters</dt><dd>%d</dd></dl></body></html>",
            app,
            app,
            ManagementFactory.getRuntimeMXBean().getUptime(),
            registry.getMeters().size());
  }
}
