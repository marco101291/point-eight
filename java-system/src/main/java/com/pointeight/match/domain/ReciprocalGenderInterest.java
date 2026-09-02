package com.pointeight.match.domain;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserSpecification;
import java.util.Objects;

/**
 * Both sides have to be interested, by gender — a candidate the seeker would swipe on but who
 * isn't seeking the seeker's gender back isn't a real candidate. This is Layer 1 only: it never
 * looks at attachment style, communication profile, or anything else from Layer 2.
 */
public record ReciprocalGenderInterest(User seeker) implements UserSpecification {

  public ReciprocalGenderInterest {
    Objects.requireNonNull(seeker, "seeker");
  }

  @Override
  public boolean isSatisfiedBy(User candidate) {
    boolean seekerWantsCandidate =
        seeker.profile().seekingGenders().contains(candidate.profile().gender());
    boolean candidateWantsSeeker =
        candidate.profile().seekingGenders().contains(seeker.profile().gender());
    return seekerWantsCandidate && candidateWantsSeeker;
  }
}
