package com.pointeight.match.application;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.ExpiryDurationPolicy;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
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
 * match is still {@code PENDING}. If activation already happened by the time the score arrives,
 * the match simply keeps whichever duration was in effect at that moment; nothing here tries to
 * synchronize activation with scoring.
 */
@Service
public class ApplyCompatibilityScoreUseCase {

  private final MatchRepository matches;
  private final ExpiryDurationPolicy expiryPolicy;

  public ApplyCompatibilityScoreUseCase(
      MatchRepository matches,
      @Value("${pointeight.match.expiry-floor-seconds}") long expiryFloorSeconds,
      @Value("${pointeight.match.expiry-ceiling-seconds}") long expiryCeilingSeconds,
      @Value("${pointeight.match.compatibility-simulation-max-days}") int simulationMaxDays) {
    this.matches = matches;
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
    }
    matches.save(match);
  }
}
