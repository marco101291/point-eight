package com.pointeight.match.application;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.user.domain.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MatchQueries {

  private final MatchRepository matches;

  public MatchQueries(MatchRepository matches) {
    this.matches = matches;
  }

  public Match byId(MatchId id) {
    return matches.findById(id).orElseThrow(() -> new MatchNotFoundException(id));
  }

  public List<Match> page(MatchStatus status, int page, int size) {
    return matches.findAll(status, page, size);
  }

  public long total(MatchStatus status) {
    return matches.count(status);
  }

  public List<Match> forUser(UserId userId) {
    return matches.findByUser(userId);
  }
}
