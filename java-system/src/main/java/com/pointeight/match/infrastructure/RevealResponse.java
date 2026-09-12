package com.pointeight.match.infrastructure;

import com.pointeight.match.application.ActiveMatchReveal;
import com.pointeight.user.domain.Profile;
import java.time.Instant;
import java.util.List;

/**
 * Layer 1 + photo of the counterpart in the caller's active match, plus when that match expires —
 * the only things the mobile reveal screen (DEC-021) is allowed to see. Declares exclusively those
 * fields, the same structural guarantee {@code UserResponse} already gives Layer 2: there's simply
 * nowhere for anything else — including a name, which doesn't exist anywhere in this system — to
 * go.
 */
public record RevealResponse(
    int age,
    String city,
    String profession,
    List<String> hobbies,
    String photoUrl,
    Instant expiresAt) {

  public static RevealResponse from(ActiveMatchReveal reveal) {
    Profile profile = reveal.profile();
    return new RevealResponse(
        profile.age(),
        profile.city(),
        profile.profession(),
        profile.hobbies(),
        profile.photoUrl(),
        reveal.expiresAt());
  }
}
