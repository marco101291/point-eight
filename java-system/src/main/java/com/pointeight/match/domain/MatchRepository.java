package com.pointeight.match.domain;

import com.pointeight.user.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Outbound port of the Match aggregate. */
public interface MatchRepository {

  Match save(Match match);

  Optional<Match> findById(MatchId id);

  /** Page of matches ordered by creation date descending, filterable by status. */
  List<Match> findAll(MatchStatus status, int page, int size);

  long count(MatchStatus status);

  /** Matches the user takes part in, on either side. */
  List<Match> findByUser(UserId userId);

  /** Whether the user already has an unfinished match (PENDING or ACTIVE). */
  boolean hasOpenMatch(UserId userId);
}
