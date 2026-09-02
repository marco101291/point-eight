package com.pointeight.match.application;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The other half of {@link RequestCompatibilityScoreUseCase}: applies a score that arrived
 * asynchronously from the Engine. Triggered by {@code CompatibilityScoreResponseListener}, not by
 * an HTTP request — there's no controller for this.
 */
@Service
public class ApplyCompatibilityScoreUseCase {

  private final MatchRepository matches;

  public ApplyCompatibilityScoreUseCase(MatchRepository matches) {
    this.matches = matches;
  }

  @Transactional
  public void execute(MatchId matchId, double score) {
    Match match = matches.findById(matchId).orElseThrow(() -> new MatchNotFoundException(matchId));
    match.assignCompatibilityScore(CompatibilityScore.of(score));
    matches.save(match);
  }
}
