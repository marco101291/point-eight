package com.pointeight.auth.infrastructure;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.Email;
import com.pointeight.auth.domain.HashedPassword;
import com.pointeight.user.domain.UserId;

/** Translates between the pure domain type and its JPA representation. */
final class AccountJpaMapper {

  private AccountJpaMapper() {}

  static AccountJpaEntity toEntity(Account account, AccountJpaEntity target) {
    AccountJpaEntity entity =
        target == null ? new AccountJpaEntity(account.userId().value()) : target;
    entity.setEmail(account.email().value());
    entity.setPasswordHash(account.password().value());
    entity.setCreatedAt(account.createdAt());
    entity.setPushToken(account.pushToken().orElse(null));
    return entity;
  }

  static Account toDomain(AccountJpaEntity entity) {
    return Account.rehydrate(
        new UserId(entity.getUserId()),
        new Email(entity.getEmail()),
        new HashedPassword(entity.getPasswordHash()),
        entity.getCreatedAt(),
        entity.getPushToken());
  }
}
