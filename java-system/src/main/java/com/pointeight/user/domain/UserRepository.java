package com.pointeight.user.domain;

import java.util.List;
import java.util.Optional;

/** Outbound port of the User aggregate. No Spring or JPA types. */
public interface UserRepository {

  User save(User user);

  Optional<User> findById(UserId id);

  /** Page of users ordered by signup date, descending. */
  List<User> findAll(int page, int size);

  long count();

  boolean existsById(UserId id);

  void deleteById(UserId id);
}
