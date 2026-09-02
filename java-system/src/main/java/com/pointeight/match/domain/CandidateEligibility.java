package com.pointeight.match.domain;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserSpecification;

/**
 * Composes the standard Layer 1 eligibility rule for a seeker: not themselves, and mutual gender
 * interest. Availability (does the candidate already have an open match?) isn't a Layer 1 rule —
 * it depends on {@code MatchRepository}, so it's the caller's job, not this Specification's.
 */
public final class CandidateEligibility {

  private CandidateEligibility() {}

  public static UserSpecification of(User seeker) {
    return new NotSelf(seeker.id()).and(new ReciprocalGenderInterest(seeker));
  }
}
