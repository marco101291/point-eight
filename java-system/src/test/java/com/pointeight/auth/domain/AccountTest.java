package com.pointeight.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AccountTest {

  @Test
  void register_stamps_the_current_time_from_the_clock() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    Account account =
        Account.register(
            UserId.newId(), new Email("marco@example.com"), new HashedPassword("hash"), fixed);

    assertThat(account.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void two_accounts_with_the_same_user_id_are_equal_regardless_of_other_fields() {
    UserId userId = UserId.newId();
    Account a =
        Account.rehydrate(
            userId, new Email("a@example.com"), new HashedPassword("hash-a"), Instant.now(), null);
    Account b =
        Account.rehydrate(
            userId, new Email("b@example.com"), new HashedPassword("hash-b"), Instant.now(), null);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void to_string_never_includes_the_password_hash() {
    Account account =
        Account.rehydrate(
            UserId.newId(),
            new Email("marco@example.com"),
            new HashedPassword("super-secret-hash"),
            Instant.now(),
            null);

    assertThat(account.toString()).doesNotContain("super-secret-hash");
  }

  @Test
  void a_freshly_registered_account_has_no_push_token() {
    Account account =
        Account.register(
            UserId.newId(),
            new Email("marco@example.com"),
            new HashedPassword("hash"),
            Clock.systemUTC());

    assertThat(account.pushToken()).isEmpty();
  }

  @Test
  void registering_a_push_token_makes_it_available() {
    Account account =
        Account.register(
            UserId.newId(),
            new Email("marco@example.com"),
            new HashedPassword("hash"),
            Clock.systemUTC());

    account.registerPushToken("ExponentPushToken[abc123]");

    assertThat(account.pushToken()).contains("ExponentPushToken[abc123]");
  }

  @Test
  void registering_a_new_push_token_replaces_the_old_one() {
    Account account =
        Account.register(
            UserId.newId(),
            new Email("marco@example.com"),
            new HashedPassword("hash"),
            Clock.systemUTC());
    account.registerPushToken("ExponentPushToken[old]");

    account.registerPushToken("ExponentPushToken[new]");

    assertThat(account.pushToken()).contains("ExponentPushToken[new]");
  }
}
