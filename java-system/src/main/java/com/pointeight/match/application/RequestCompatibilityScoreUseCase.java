package com.pointeight.match.application;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.simulation.domain.AgentSnapshot;
import com.pointeight.simulation.domain.CompatibilityEnginePort;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks the Engine for a score for an already-created match. Kept separate from {@link
 * CreateManualMatchUseCase} on purpose: the trigger is {@code MatchAssignedEvent}/a manual
 * request, not match creation itself, so that change won't touch this class.
 *
 * <p>Fire-and-forget since M4 (DEC in docs/architecture.md): this publishes the request over
 * {@code CompatibilityEnginePort} and returns immediately, with the match's score still whatever
 * it was before the call — {@link ApplyCompatibilityScoreUseCase} is what applies the score, once
 * it arrives asynchronously.
 */
@Service
public class RequestCompatibilityScoreUseCase {

  private final MatchRepository matches;
  private final UserRepository users;
  private final CompatibilityEnginePort engine;

  public RequestCompatibilityScoreUseCase(
      MatchRepository matches, UserRepository users, CompatibilityEnginePort engine) {
    this.matches = matches;
    this.users = users;
    this.engine = engine;
  }

  @Transactional
  public Match execute(MatchId id) {
    Match match = matches.findById(id).orElseThrow(() -> new MatchNotFoundException(id));

    User userA = requireUser(match.userAId());
    User userB = requireUser(match.userBId());

    engine.requestAssessment(
        id,
        new AgentSnapshot(userA.profile(), userA.simulationParameters()),
        new AgentSnapshot(userB.profile(), userB.simulationParameters()));

    return match;
  }

  private User requireUser(UserId id) {
    return users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
  }
}
