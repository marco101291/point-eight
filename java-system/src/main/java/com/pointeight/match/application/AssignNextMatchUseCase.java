package com.pointeight.match.application;

import com.pointeight.match.domain.CandidateEligibility;
import com.pointeight.match.domain.CandidateSelectionStrategy;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the next match for a user whose previous one just ran its course. Triggered by {@link
 * com.pointeight.match.infrastructure.MatchExpiredEventListener}, one call per user in the expired
 * match — it doesn't know or care that it's being called as a reaction to an event.
 *
 * <p>Reuses {@link CreateManualMatchUseCase} instead of duplicating its checks: this class's only
 * job is deciding <em>who</em>, not re-validating the invariants around creating a match.
 *
 * <p>{@code REQUIRES_NEW}, not the default propagation: the listener that calls this runs {@code
 * AFTER_COMMIT} of {@code expire()}'s transaction, but that transaction's {@code EntityManager} is
 * only unbound from the thread in {@code afterCompletion()} — which runs after every {@code
 * afterCommit()} callback, this one included. With the default propagation this method would
 * silently join that already-committed, soon-to-be-discarded resource instead of opening its own,
 * and since it isn't that transaction's owner, nothing it does here would ever actually commit.
 */
@Service
public class AssignNextMatchUseCase {

  private final UserRepository users;
  private final MatchRepository matches;
  private final CreateManualMatchUseCase createMatch;
  private final CandidateSelectionStrategy selectionStrategy;
  private final RandomGenerator random;
  private final Duration defaultExpiry;
  private final int candidatePoolSize;

  public AssignNextMatchUseCase(
      UserRepository users,
      MatchRepository matches,
      CreateManualMatchUseCase createMatch,
      CandidateSelectionStrategy selectionStrategy,
      RandomGenerator random,
      @Value("${pointeight.match.default-expiry-seconds}") long defaultExpirySeconds,
      @Value("${pointeight.match.candidate-pool-size:50}") int candidatePoolSize) {
    this.users = users;
    this.matches = matches;
    this.createMatch = createMatch;
    this.selectionStrategy = selectionStrategy;
    this.random = random;
    this.defaultExpiry = Duration.ofSeconds(defaultExpirySeconds);
    this.candidatePoolSize = candidatePoolSize;
  }

  /**
   * Empty means "nobody eligible right now" — not an error. The seeker having an open match
   * already (e.g. their ex-partner's own search just picked them) is also not an error: it means
   * there's nothing left to do here.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<Match> execute(UserId seekerId) {
    if (matches.hasOpenMatch(seekerId)) {
      return Optional.empty();
    }

    User seeker = users.findById(seekerId).orElseThrow(() -> new UserNotFoundException(seekerId));

    // MVP candidate pool: the first N registered users, filtered in memory. There's no query yet
    // that pushes Layer 1 reciprocity or availability down to the database — fine at this scale,
    // not something to carry into M5 unexamined.
    List<User> pool = users.findAll(0, candidatePoolSize);
    var eligibility = CandidateEligibility.of(seeker);
    List<User> eligibleCandidates =
        pool.stream()
            .filter(eligibility::isSatisfiedBy)
            .filter(candidate -> !matches.hasOpenMatch(candidate.id()))
            .toList();

    return selectionStrategy
        .selectFor(seeker, eligibleCandidates, random)
        .map(candidate -> createMatch.execute(seekerId, candidate.id(), defaultExpiry));
  }
}
