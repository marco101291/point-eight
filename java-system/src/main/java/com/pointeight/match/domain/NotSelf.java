package com.pointeight.match.domain;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserSpecification;
import java.util.Objects;

/** A candidate can't be the same person as the one being matched. */
public record NotSelf(UserId excludedId) implements UserSpecification {

  public NotSelf {
    Objects.requireNonNull(excludedId, "excludedId");
  }

  @Override
  public boolean isSatisfiedBy(User candidate) {
    return !candidate.id().equals(excludedId);
  }
}
