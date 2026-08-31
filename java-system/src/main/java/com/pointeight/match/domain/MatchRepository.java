package com.pointeight.match.domain;

import com.pointeight.user.domain.UserId;
import java.util.List;
import java.util.Optional;

/** Port de salida del aggregate Match. */
public interface MatchRepository {

  Match save(Match match);

  Optional<Match> findById(MatchId id);

  /** Página de matches ordenada por fecha de creación descendente, filtrable por estado. */
  List<Match> findAll(MatchStatus status, int page, int size);

  long count(MatchStatus status);

  /** Matches en los que participa el usuario, en cualquiera de los dos lados. */
  List<Match> findByUser(UserId userId);

  /** Si el usuario ya tiene un match sin terminar (PENDING o ACTIVE). */
  boolean hasOpenMatch(UserId userId);
}
