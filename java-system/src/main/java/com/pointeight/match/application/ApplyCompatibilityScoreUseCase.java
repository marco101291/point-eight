package com.pointeight.match.application;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.ExpiryDurationPolicy;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.shared.domain.DomainEventPublisher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The other half of {@link RequestCompatibilityScoreUseCase}: applies a score that arrived
 * asynchronously from the Engine. Triggered by {@code CompatibilityScoreResponseListener}, not by
 * an HTTP request — there's no controller for this.
 *
 * <p>DEC-028: also derives the match's real duration from {@code expiryDays} via {@link
 * ExpiryDurationPolicy}, replacing the flat default assigned at proposal — but only while the
 * match is still {@code PENDING}.
 *
 * <p>DEC-032: and then activates it, in the same transaction. The score is the last thing the
 * System was waiting for — "it evaluates, then it reveals" — so a {@code PENDING} match no longer
 * needs anyone calling {@code POST /api/matches/{id}/activate} by hand. Order matters: duration
 * first, because {@code activate()} anchors {@code expiresAt} to whatever duration is in effect
 * at that instant and {@code applyExpiryDuration()} refuses anything past {@code PENDING}. A match
 * someone already activated by hand keeps its duration and isn't touched again.
 */
@Service
public class ApplyCompatibilityScoreUseCase {

  private final MatchRepository matches;
  private final DomainEventPublisher events;
  private final Clock clock;
  private final ExpiryDurationPolicy expiryPolicy;

  public ApplyCompatibilityScoreUseCase(
      MatchRepository matches,
      DomainEventPublisher events,
      Clock clock,
      @Value("${pointeight.match.expiry-floor-seconds}") long expiryFloorSeconds,
      @Value("${pointeight.match.expiry-ceiling-seconds}") long expiryCeilingSeconds,
      @Value("${pointeight.match.compatibility-simulation-max-days}") int simulationMaxDays) {
    this.matches = matches;
    this.events = events;
    this.clock = clock;
    this.expiryPolicy =
        new ExpiryDurationPolicy(
            Duration.ofSeconds(expiryFloorSeconds),
            Duration.ofSeconds(expiryCeilingSeconds),
            simulationMaxDays);
  }

  @Transactional
  public void execute(MatchId matchId, double score, int expiryDays) {
    Match match = matches.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
    match.assignCompatibilityScore(CompatibilityScore.of(score));
    if (match.status() == MatchStatus.PENDING) {
      match.applyExpiryDuration(expiryPolicy.durationFor(expiryDays));
      match.activate(clock);
    }
    matches.save(match);
    // MatchActivatedEvent -> push notification (DEC-024), AFTER_COMMIT like every other listener.
    events.publishAll(match.pullEvents());
  }
}
