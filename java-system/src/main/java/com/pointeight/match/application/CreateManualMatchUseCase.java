package com.pointeight.match.application;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.shared.domain.DomainEventPublisher;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea un match entre dos IDs dados. En M1 lo dispara un operador desde el admin panel; desde M4 lo
 * hace el Sistema solo al vencer el match anterior.
 */
@Service
public class CreateManualMatchUseCase {

  private final MatchRepository matches;
  private final UserRepository users;
  private final DomainEventPublisher events;
  private final Clock clock;

  public CreateManualMatchUseCase(
      MatchRepository matches, UserRepository users, DomainEventPublisher events, Clock clock) {
    this.matches = matches;
    this.users = users;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public Match execute(UserId userAId, UserId userBId, Duration expiryDuration) {
    requireExists(userAId);
    requireExists(userBId);
    requireAvailable(userAId);
    requireAvailable(userBId);

    Match match = Match.propose(userAId, userBId, expiryDuration, clock);
    Match saved = matches.save(match);
    events.publishAll(match.pullEvents());
    return saved;
  }

  private void requireExists(UserId id) {
    if (!users.existsById(id)) {
      throw new UserNotFoundException(id);
    }
  }

  private void requireAvailable(UserId id) {
    if (matches.hasOpenMatch(id)) {
      throw new UserAlreadyMatchedException(id);
    }
  }
}
