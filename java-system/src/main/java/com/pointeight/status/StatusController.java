package com.pointeight.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de vida del servicio, usado por el healthcheck y por el admin panel. */
@RestController
@RequestMapping("/api")
public class StatusController {

  @GetMapping("/status")
  public SystemStatus status() {
    return new SystemStatus("el-sistema", "0.0.1-SNAPSHOT", "M1", "El compound está en línea.");
  }

  /** Value Object de respuesta. */
  public record SystemStatus(String service, String version, String milestone, String message) {}
}
