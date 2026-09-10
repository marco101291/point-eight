package com.pointeight.auth.domain;

import com.pointeight.user.domain.UserId;
import java.util.Optional;

/** Outbound port of the Account entity. No Spring or JPA types. */
public interface AccountRepository {

  Account save(Account account);

  Optional<Account> findByEmail(Email email);

  Optional<Account> findByUserId(UserId userId);

  boolean existsByEmail(Email email);
}
