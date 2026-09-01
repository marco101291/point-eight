package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.CompatibilityScore;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchStatus;
import java.time.Instant;

/** External view of a match. {@code compatibilityScore} is null until the Engine calculates it. */
public record MatchResponse(
    String id,
    String userAId,
    String userBId,
    MatchStatus status,
    long expiryDurationSeconds,
    Double compatibilityScore,
    Instant createdAt,
    Instant activatedAt,
    Instant expiresAt,
    Instant endedAt) {

  public static MatchResponse from(Match match) {
    return new MatchResponse(
        match.id().toString(),
        match.userAId().toString(),
        match.userBId().toString(),
        match.status(),
        match.expiryDuration().toSeconds(),
        match.compatibilityScore().map(CompatibilityScore::value).orElse(null),
        match.createdAt(),
        match.activatedAt().orElse(null),
        match.expiresAt().orElse(null),
        match.endedAt().orElse(null));
  }
}
