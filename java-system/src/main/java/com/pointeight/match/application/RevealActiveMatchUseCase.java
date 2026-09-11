package com.pointeight.match.application;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the mobile client's reveal screen (DEC-021) shows: the counterpart's Layer 1 profile and
 * photo for the caller's currently ACTIVE match, plus when that match expires — the countdown the
 * reveal screen gates the full profile behind. There's at most one active match — DEC-007's
 * one-open-match-per-user invariant — so "the" active match, not a list.
 */
@Service
@Transactional(readOnly = true)
public class RevealActiveMatchUseCase {

  private final MatchRepository matches;
  private final UserRepository users;

  public RevealActiveMatchUseCase(MatchRepository matches, UserRepository users) {
    this.matches = matches;
    this.users = users;
  }

  public ActiveMatchReveal execute(UserId requester) {
    Match active =
        matches.findByUser(requester).stream()
            .filter(match -> match.status() == MatchStatus.ACTIVE)
            .findFirst()
            .orElseThrow(() -> new NoActiveMatchException(requester));

    UserId counterpartId =
        active.userAId().equals(requester) ? active.userBId() : active.userAId();
    Profile profile =
        users
            .findById(counterpartId)
            .orElseThrow(() -> new UserNotFoundException(counterpartId))
            .profile();

    // activate() always stamps activatedAt, so an ACTIVE match's expiresAt() is never empty in
    // practice — the exception here is a defensive "this invariant broke," not an expected path.
    var expiresAt =
        active
            .expiresAt()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "ACTIVE match %s has no expiresAt".formatted(active.id())));

    return new ActiveMatchReveal(profile, expiresAt);
  }
}
