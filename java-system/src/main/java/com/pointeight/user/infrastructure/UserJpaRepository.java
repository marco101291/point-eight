package com.pointeight.user.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository. An infrastructure detail, not the domain port. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {}
