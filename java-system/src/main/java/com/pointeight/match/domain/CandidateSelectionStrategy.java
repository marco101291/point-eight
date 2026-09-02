package com.pointeight.match.domain;

import com.pointeight.user.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Chooses one candidate for the seeker out of an already-eligible pool. Eligibility (Layer 1
 * reciprocity, availability) is decided before this is called — see {@link CandidateEligibility}
 * — so a strategy only ever has to decide "which one", not "who counts".
 *
 * <p>{@code random} is a method parameter, not a constructor-injected field, for the same reason
 * {@code Match.propose(..., Clock clock)} takes a {@code Clock} instead of holding one: this stays
 * a plain, stateless domain type that doesn't need Spring to exist, and tests can pass a seeded
 * {@code RandomGenerator} for a reproducible pick.
 */
public interface CandidateSelectionStrategy {

  Optional<User> selectFor(User seeker, List<User> eligibleCandidates, RandomGenerator random);
}
