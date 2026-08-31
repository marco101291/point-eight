package com.pointeight.user.domain;

import java.util.List;
import java.util.Optional;

/** Port de salida del aggregate User. Sin tipos de Spring ni de JPA. */
public interface UserRepository {

  User save(User user);

  Optional<User> findById(UserId id);

  /** Página de usuarios ordenada por fecha de alta descendente. */
  List<User> findAll(int page, int size);

  long count();

  boolean existsById(UserId id);

  void deleteById(UserId id);
}
