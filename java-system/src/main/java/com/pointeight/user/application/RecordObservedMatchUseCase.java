package com.pointeight.user.application;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Post-match recalibration (DEC-026): raises the user's {@code cumulativeConfidenceScore} once per
 * match that actually ran. Takes a bare {@link UserId} on purpose — the {@code user} feature knows
 * nothing about matches; deciding which matches count is the {@code match} side's job.
 */
@Service
public class RecordObservedMatchUseCase {

  private final UserRepository users;
  private final Clock clock;

  public RecordObservedMatchUseCase(UserRepository users, Clock clock) {
    this.users = users;
    this.clock = clock;
  }

  /**
   * {@code REQUIRES_NEW} for the same reason as {@code AssignNextMatchUseCase} (DEC-015): it's
   * called from an {@code AFTER_COMMIT} listener, where the match's transaction is already
   * committed but still bound to the thread. A plain {@code REQUIRED} would join that finished
   * transaction and this write would never reach the database.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User execute(UserId userId) {
    User user = users.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    user.recordObservedMatch(clock);
    return users.save(user);
  }
}
