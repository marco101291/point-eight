package com.pointeight.auth.infrastructure;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data repository. An infrastructure detail, not the domain port.
 *
 * <p>{@code claim} and {@code deleteByFamilyId} are each annotated {@code @Transactional} right
 * here, not left to whatever transaction (if any) the caller happens to be in — unlike {@code
 * save}/{@code findById} (inherited from {@code JpaRepository}, already transactional via {@code
 * SimpleJpaRepository}'s own class-level annotation), a custom {@code @Modifying} query has no
 * transaction of its own by default and throws {@code TransactionRequiredException} without one.
 * Giving each its own transaction here means {@code RefreshAccessTokenUseCase} can call them as
 * independent, immediately-committing steps with no surrounding {@code @Transactional} of its
 * own — see that class's javadoc for why: wrapping them together deadlocks.
 */
interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, String> {

  /**
   * A single conditional {@code UPDATE ... WHERE used_at IS NULL}, not a read-then-write from
   * application code: Postgres's own row locking is what makes this atomic across two concurrent
   * transactions racing on the same row, which two separate JPA calls (find, check, save) never
   * could be. Returns the number of rows actually updated — 0 or 1, never more, since {@code
   * token_hash} is the primary key.
   */
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE RefreshTokenJpaEntity t SET t.usedAt = :usedAt "
          + "WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL")
  int claim(@Param("tokenHash") String tokenHash, @Param("usedAt") Instant usedAt);

  /** Derived delete query — Spring Data generates the "select matching, then remove" itself. */
  @Transactional
  void deleteByFamilyId(String familyId);
}
