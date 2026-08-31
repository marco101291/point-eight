package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import com.pointeight.user.domain.UserId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MatchRepositoryAdapter implements MatchRepository {

  /** Un match sin terminar ocupa al usuario: no puede recibir otro. */
  private static final Set<MatchStatus> OPEN =
      EnumSet.of(MatchStatus.PENDING, MatchStatus.ACTIVE);

  private final MatchJpaRepository jpa;

  public MatchRepositoryAdapter(MatchJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Match save(Match match) {
    MatchJpaEntity existing = jpa.findById(match.id().value()).orElse(null);
    return MatchJpaMapper.toDomain(jpa.save(MatchJpaMapper.toEntity(match, existing)));
  }

  @Override
  public Optional<Match> findById(MatchId id) {
    return jpa.findById(id.value()).map(MatchJpaMapper::toDomain);
  }

  @Override
  public List<Match> findAll(MatchStatus status, int page, int size) {
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    var results = status == null ? jpa.findAll(pageable) : jpa.findByStatus(status, pageable);
    return results.map(MatchJpaMapper::toDomain).getContent();
  }

  @Override
  public long count(MatchStatus status) {
    return status == null ? jpa.count() : jpa.countByStatus(status);
  }

  @Override
  public List<Match> findByUser(UserId userId) {
    return jpa.findByUser(userId.value()).stream().map(MatchJpaMapper::toDomain).toList();
  }

  @Override
  public boolean hasOpenMatch(UserId userId) {
    return jpa.existsByUserAndStatusIn(userId.value(), OPEN);
  }
}
