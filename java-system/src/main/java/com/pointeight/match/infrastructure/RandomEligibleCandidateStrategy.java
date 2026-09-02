package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.CandidateSelectionStrategy;
import com.pointeight.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

/**
 * The only strategy M4 needs: pick uniformly at random among whoever is eligible. A future
 * strategy could rank by {@code cumulativeConfidenceScore} or by predicted compatibility instead
 * — the point of naming this as Strategy is that swapping it out later doesn't touch the use case
 * that calls it.
 *
 * <p>Lives in {@code infrastructure/}, not {@code domain/}, even though its logic is pure — same
 * split as {@code CompatibilityEnginePort}/{@code EngineCompatibilityClient}: the port interface
 * stays in {@code domain/}, and whichever implementation Spring is supposed to wire in lives in
 * {@code infrastructure/} as the adapter, regardless of whether that implementation happens to do
 * I/O or not.
 */
@Component
public class RandomEligibleCandidateStrategy implements CandidateSelectionStrategy {

  @Override
  public Optional<User> selectFor(User seeker, List<User> eligibleCandidates, RandomGenerator random) {
    if (eligibleCandidates.isEmpty()) {
      return Optional.empty();
    }
    int index = random.nextInt(eligibleCandidates.size());
    return Optional.of(eligibleCandidates.get(index));
  }
}
