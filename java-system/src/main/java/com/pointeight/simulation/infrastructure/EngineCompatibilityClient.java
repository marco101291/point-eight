package com.pointeight.simulation.infrastructure;

import com.pointeight.simulation.domain.AgentSnapshot;
import com.pointeight.simulation.domain.CompatibilityAssessment;
import com.pointeight.simulation.domain.CompatibilityEngineException;
import com.pointeight.simulation.domain.CompatibilityEnginePort;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Synchronous REST adapter to {@code POST /api/v1/compatibility} on the Engine. */
@Component
public class EngineCompatibilityClient implements CompatibilityEnginePort {

  private static final String MODEL_VERSION = "v0";

  private final RestClient engineRestClient;

  public EngineCompatibilityClient(RestClient engineRestClient) {
    this.engineRestClient = engineRestClient;
  }

  @Override
  public CompatibilityAssessment assess(AgentSnapshot agentA, AgentSnapshot agentB) {
    CompatibilityRequestDto request =
        new CompatibilityRequestDto(MODEL_VERSION, AgentDto.from(agentA), AgentDto.from(agentB));

    CompatibilityResponseDto response;
    try {
      response =
          engineRestClient
              .post()
              .uri("/api/v1/compatibility")
              .body(request)
              .retrieve()
              .body(CompatibilityResponseDto.class);
    } catch (RestClientException e) {
      throw new CompatibilityEngineException("The Engine didn't respond: " + e.getMessage(), e);
    }

    if (response == null) {
      throw new CompatibilityEngineException("The Engine returned an empty response", null);
    }

    return new CompatibilityAssessment(
        response.compatibilityScore(), Duration.ofDays(response.expiryDays()));
  }
}
