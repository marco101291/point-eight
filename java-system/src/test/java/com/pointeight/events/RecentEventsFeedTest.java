package com.pointeight.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.event.MatchActivatedEvent;
import com.pointeight.match.domain.event.MatchAssignedEvent;
import com.pointeight.match.domain.event.MatchExpiredEvent;
import com.pointeight.shared.domain.DomainEvent;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecentEventsFeedTest {

  private final UserRepository users = mock(UserRepository.class);
  private final RecentEventsFeed feed = new RecentEventsFeed(users);

  private static User sampleUser(String city, String profession) {
    return User.register(
        new Profile(
            28, Gender.MALE, Set.of(Gender.FEMALE), SeekingType.LONG_TERM, city, profession,
            List.of(), "https://picsum.photos/seed/test/900/1400"),
        null,
        Clock.systemUTC());
  }

  @Test
  void records_a_match_assigned_event_with_the_users_profile_snapshot() {
    UserId userAId = UserId.newId();
    UserId userBId = UserId.newId();
    MatchId matchId = MatchId.newId();
    when(users.findById(userAId)).thenReturn(Optional.of(sampleUser("Madrid", "docente")));
    when(users.findById(userBId)).thenReturn(Optional.of(sampleUser("Cordoba", "chef")));

    feed.on(new MatchAssignedEvent(matchId, userAId, userBId, Duration.ofHours(1), Instant.now()));

    RecentEvent recorded = feed.since(0).get(0);
    assertThat(recorded.type()).isEqualTo("MatchAssignedEvent");
    assertThat(recorded.matchId()).isEqualTo(matchId.toString());
    assertThat(recorded.userA())
        .isEqualTo(new UserSummary(userAId.toString(), "Madrid", "docente"));
    assertThat(recorded.userB()).isEqualTo(new UserSummary(userBId.toString(), "Cordoba", "chef"));
    assertThat(recorded.expiryDurationSeconds()).isEqualTo(3600L);
  }

  @Test
  void records_a_match_activated_event() {
    UserId userAId = UserId.newId();
    UserId userBId = UserId.newId();
    MatchId matchId = MatchId.newId();
    when(users.findById(userAId)).thenReturn(Optional.of(sampleUser("Rosario", "chef")));
    when(users.findById(userBId)).thenReturn(Optional.of(sampleUser("Cordoba", "musico")));

    feed.on(new MatchActivatedEvent(matchId, userAId, userBId, Instant.now()));

    RecentEvent recorded = feed.since(0).get(0);
    assertThat(recorded.type()).isEqualTo("MatchActivatedEvent");
    assertThat(recorded.matchId()).isEqualTo(matchId.toString());
    assertThat(recorded.expiryDurationSeconds()).isNull();
  }

  @Test
  void falls_back_to_a_null_profile_when_the_user_no_longer_exists() {
    UserId userAId = UserId.newId();
    when(users.findById(any())).thenReturn(Optional.empty());

    feed.on(new MatchExpiredEvent(MatchId.newId(), userAId, UserId.newId(), Instant.now()));

    UserSummary summary = feed.since(0).get(0).userA();
    assertThat(summary.id()).isEqualTo(userAId.toString());
    assertThat(summary.city()).isNull();
    assertThat(summary.profession()).isNull();
  }

  @Test
  void records_a_match_expired_event_with_no_expiry_duration() {
    when(users.findById(any())).thenReturn(Optional.empty());
    MatchId matchId = MatchId.newId();

    feed.on(new MatchExpiredEvent(matchId, UserId.newId(), UserId.newId(), Instant.now()));

    RecentEvent recorded = feed.since(0).get(0);
    assertThat(recorded.type()).isEqualTo("MatchExpiredEvent");
    assertThat(recorded.expiryDurationSeconds()).isNull();
  }

  @Test
  void ignores_domain_events_it_does_not_know_how_to_summarize() {
    feed.on((DomainEvent) Instant::now);

    assertThat(feed.since(0)).isEmpty();
  }

  @Test
  void since_only_returns_events_recorded_after_the_given_sequence() {
    when(users.findById(any())).thenReturn(Optional.empty());
    feed.on(new MatchExpiredEvent(MatchId.newId(), UserId.newId(), UserId.newId(), Instant.now()));
    long firstSequence = feed.since(0).get(0).sequence();
    feed.on(new MatchExpiredEvent(MatchId.newId(), UserId.newId(), UserId.newId(), Instant.now()));

    List<RecentEvent> afterFirst = feed.since(firstSequence);

    assertThat(afterFirst).hasSize(1);
    assertThat(afterFirst.get(0).sequence()).isGreaterThan(firstSequence);
  }

  @Test
  void keeps_only_the_most_recent_200_events() {
    when(users.findById(any())).thenReturn(Optional.empty());
    for (int i = 0; i < 205; i++) {
      feed.on(
          new MatchExpiredEvent(MatchId.newId(), UserId.newId(), UserId.newId(), Instant.now()));
    }

    assertThat(feed.since(0)).hasSize(200);
  }
}
