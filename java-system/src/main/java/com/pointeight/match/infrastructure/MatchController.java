package com.pointeight.match.infrastructure;

import com.pointeight.match.application.CreateManualMatchUseCase;
import com.pointeight.match.application.MatchLifecycleUseCases;
import com.pointeight.match.application.MatchQueries;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.shared.infrastructure.PageResponse;
import com.pointeight.user.domain.UserId;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Adapter de entrada HTTP del aggregate Match, incluida la máquina de estados. */
@RestController
@RequestMapping("/api/matches")
public class MatchController {

  private final CreateManualMatchUseCase createMatch;
  private final MatchLifecycleUseCases lifecycle;
  private final MatchQueries queries;
  private final Duration defaultExpiry;

  public MatchController(
      CreateManualMatchUseCase createMatch,
      MatchLifecycleUseCases lifecycle,
      MatchQueries queries,
      @Value("${pointeight.match.default-expiry-seconds}") long defaultExpirySeconds) {
    this.createMatch = createMatch;
    this.lifecycle = lifecycle;
    this.queries = queries;
    this.defaultExpiry = Duration.ofSeconds(defaultExpirySeconds);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MatchResponse create(@Valid @RequestBody CreateMatchRequest request) {
    Duration expiry =
        request.expiryDurationSeconds() == null
            ? defaultExpiry
            : Duration.ofSeconds(request.expiryDurationSeconds());
    return MatchResponse.from(
        createMatch.execute(
            UserId.of(request.userAId()), UserId.of(request.userBId()), expiry));
  }

  @GetMapping
  public PageResponse<MatchResponse> list(
      @RequestParam(required = false) MatchStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var content = queries.page(status, page, size).stream().map(MatchResponse::from).toList();
    return PageResponse.of(content, page, size, queries.total(status));
  }

  @GetMapping("/{id}")
  public MatchResponse byId(@PathVariable String id) {
    return MatchResponse.from(queries.byId(MatchId.of(id)));
  }

  @PostMapping("/{id}/activate")
  public MatchResponse activate(@PathVariable String id) {
    return MatchResponse.from(lifecycle.activate(MatchId.of(id)));
  }

  @PostMapping("/{id}/expire")
  public MatchResponse expire(@PathVariable String id) {
    return MatchResponse.from(lifecycle.expire(MatchId.of(id)));
  }

  @PostMapping("/{id}/reject")
  public MatchResponse reject(@PathVariable String id) {
    return MatchResponse.from(lifecycle.reject(MatchId.of(id)));
  }
}
