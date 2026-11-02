package com.eventhq.legacy;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class ServletConfiguration {
  @Bean
  ServletRegistrationBean<AdminMetricsServlet> adminMetrics(AdminMetricsServlet servlet) {
    return new ServletRegistrationBean<>(servlet, "/admin/legacy/metrics");
  }
}
