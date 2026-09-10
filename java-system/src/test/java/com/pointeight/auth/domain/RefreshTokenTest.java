package com.pointeight.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

  @Test
  void issuing_a_new_family_returns_a_persistable_record_and_a_raw_secret_that_hashes_to_it() {
    UserId userId = UserId.newId();

    RefreshToken.Issued issued = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);

    assertThat(issued.rawValue()).isNotBlank();
    assertThat(issued.token().tokenHash()).isEqualTo(RefreshToken.hash(issued.rawValue()));
    assertThat(issued.token().familyId()).isNotBlank();
    assertThat(issued.token().userId()).isEqualTo(userId);
    assertThat(issued.token().createdAt()).isEqualTo(T0);
    assertThat(issued.token().expiresAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
    assertThat(issued.token().usedAt()).isNull();
  }

  @Test
  void two_new_families_never_collide_in_token_or_family_id() {
    UserId userId = UserId.newId();

    RefreshToken.Issued first = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);
    RefreshToken.Issued second = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);

    assertThat(first.rawValue()).isNotEqualTo(second.rawValue());
    assertThat(first.token().tokenHash()).isNotEqualTo(second.token().tokenHash());
    assertThat(first.token().familyId()).isNotEqualTo(second.token().familyId());
  }

  @Test
  void rotating_within_a_family_keeps_the_same_family_id() {
    UserId userId = UserId.newId();
    RefreshToken.Issued original = RefreshToken.issueNewFamily(userId, Duration.ofDays(30), CLOCK);

    RefreshToken.Issued rotated =
        RefreshToken.issueInFamily(original.token().familyId(), userId, Duration.ofDays(30), CLOCK);

    assertThat(rotated.token().familyId()).isEqualTo(original.token().familyId());
    assertThat(rotated.token().tokenHash()).isNotEqualTo(original.token().tokenHash());
  }

  @Test
  void hashing_is_deterministic_so_a_presented_token_can_be_looked_up_again() {
    String raw = RefreshToken.issueNewFamily(UserId.newId(), Duration.ofDays(30), CLOCK).rawValue();

    assertThat(RefreshToken.hash(raw)).isEqualTo(RefreshToken.hash(raw));
  }

  @Test
  void is_expired_right_at_the_edge() {
    UserId userId = UserId.newId();
    RefreshToken token =
        RefreshToken.rehydrate(
            "some-hash", "some-family", userId, T0.plus(Duration.ofDays(30)), T0, null);

    Clock justBefore = Clock.fixed(T0.plus(Duration.ofDays(30)).minusSeconds(1), ZoneOffset.UTC);
    Clock exactly = Clock.fixed(T0.plus(Duration.ofDays(30)), ZoneOffset.UTC);
    Clock after = Clock.fixed(T0.plus(Duration.ofDays(30)).plusSeconds(1), ZoneOffset.UTC);

    assertThat(token.isExpired(justBefore)).isFalse();
    assertThat(token.isExpired(exactly)).isTrue();
    assertThat(token.isExpired(after)).isTrue();
  }

  @Test
  void identity_is_by_hash() {
    RefreshToken a = RefreshToken.rehydrate("same-hash", "family-a", UserId.newId(), T0, T0, null);
    RefreshToken b = RefreshToken.rehydrate("same-hash", "family-b", UserId.newId(), T0, T0, null);

    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }
}
