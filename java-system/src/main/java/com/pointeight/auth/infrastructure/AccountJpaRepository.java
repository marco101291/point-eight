package com.pointeight.auth.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository. An infrastructure detail, not the domain port. */
interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {

  Optional<AccountJpaEntity> findByEmail(String email);

  boolean existsByEmail(String email);
}
