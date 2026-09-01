package com.pointeight.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service liveness endpoint, used by the healthcheck and the admin panel. */
@RestController
@RequestMapping("/api")
public class StatusController {

  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("el-sistema", "0.0.1-SNAPSHOT", "M1", "The compound is online.");
  }

  /** Response Value Object. */
  public record SystemStatus(String service, String version, String milestone, String message) {}
}
