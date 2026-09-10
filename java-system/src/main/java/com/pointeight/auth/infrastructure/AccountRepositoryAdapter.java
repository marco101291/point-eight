package com.pointeight.auth.infrastructure;

import com.pointeight.auth.domain.Account;
import com.pointeight.auth.domain.AccountRepository;
import com.pointeight.auth.domain.Email;
import com.pointeight.user.domain.UserId;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Adapter that implements the domain port on top of Spring Data JPA. */
@Component
public class AccountRepositoryAdapter implements AccountRepository {

  private final AccountJpaRepository jpa;

  public AccountRepositoryAdapter(AccountJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Account save(Account account) {
    AccountJpaEntity existing = jpa.findById(account.userId().value()).orElse(null);
    AccountJpaEntity saved = jpa.save(AccountJpaMapper.toEntity(account, existing));
    return AccountJpaMapper.toDomain(saved);
  }

  @Override
  public Optional<Account> findByEmail(Email email) {
    return jpa.findByEmail(email.value()).map(AccountJpaMapper::toDomain);
  }

  @Override
  public Optional<Account> findByUserId(UserId userId) {
    return jpa.findById(userId.value()).map(AccountJpaMapper::toDomain);
  }

  @Override
  public boolean existsByEmail(Email email) {
    return jpa.existsByEmail(email.value());
  }
}
