package com.pointeight.user.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de Spring Data. Detalle de infraestructura, no es el port del dominio. */
interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {}
