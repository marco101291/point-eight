package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** Adapter que implementa el port del dominio sobre Spring Data JPA. */
@Component
public class UserRepositoryAdapter implements UserRepository {

  private final UserJpaRepository jpa;

  public UserRepositoryAdapter(UserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public User save(User user) {
    // Reusa la fila existente si la hay, para no perder la versión ni duplicar colecciones.
    UserJpaEntity existing = jpa.findById(user.id().value()).orElse(null);
    UserJpaEntity saved = jpa.save(UserJpaMapper.toEntity(user, existing));
    return UserJpaMapper.toDomain(saved);
  }

  @Override
  public Optional<User> findById(UserId id) {
    return jpa.findById(id.value()).map(UserJpaMapper::toDomain);
  }

  @Override
  public List<User> findAll(int page, int size) {
    return jpa
        .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
        .map(UserJpaMapper::toDomain)
        .getContent();
  }

  @Override
  public long count() {
    return jpa.count();
  }

  @Override
  public boolean existsById(UserId id) {
    return jpa.existsById(id.value());
  }

  @Override
  public void deleteById(UserId id) {
    jpa.deleteById(id.value());
  }
}
