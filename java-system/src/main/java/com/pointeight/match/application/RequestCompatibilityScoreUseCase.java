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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks the Engine for a score for an already-created match. Kept separate from {@link
 * CreateManualMatchUseCase} on purpose: the trigger is {@code MatchAssignedEvent} (DEC-028,
 * {@code MatchAssignedEventListener}) or a manual request (the {@code /score} endpoint), not
 * match creation itself, so that change won't touch this class.
 *
 * <p>Fire-and-forget since M4 (DEC in docs/architecture.md): this publishes the request over
 * {@code CompatibilityEnginePort} and returns immediately, with the match's score still whatever
 * it was before the call — {@link ApplyCompatibilityScoreUseCase} is what applies the score, once
 * it arrives asynchronously.
 *
 * <p>{@code REQUIRES_NEW}, same reasoning {@code AssignNextMatchUseCase} already documents: when
 * called from {@code MatchAssignedEventListener}'s {@code AFTER_COMMIT} callback, the calling
 * transaction's {@code EntityManager} is on its way out, so the default propagation would
 * silently join that soon-to-be-discarded resource instead of opening a real one of its own. Safe
 * for the manual HTTP path too — {@code REQUIRES_NEW} behaves like a plain new transaction when
 * there's no surrounding one to suspend.
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
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
