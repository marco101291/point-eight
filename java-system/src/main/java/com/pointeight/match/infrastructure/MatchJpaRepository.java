package com.pointeight.match.infrastructure;

import com.pointeight.match.domain.MatchStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MatchJpaRepository extends JpaRepository<MatchJpaEntity, UUID> {

  Page<MatchJpaEntity> findByStatus(MatchStatus status, Pageable pageable);

  long countByStatus(MatchStatus status);

  @Query("select m from MatchJpaEntity m where m.userAId = :userId or m.userBId = :userId"
      + " order by m.createdAt desc")
  List<MatchJpaEntity> findByUser(@Param("userId") UUID userId);

  @Query("select count(m) > 0 from MatchJpaEntity m"
      + " where (m.userAId = :userId or m.userBId = :userId) and m.status in :statuses")
  boolean existsByUserAndStatusIn(
      @Param("userId") UUID userId, @Param("statuses") Collection<MatchStatus> statuses);
}
