package com.pointeight.match.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.match.domain.CandidateSelectionStrategy;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.User;
import com.pointeight.user.domain.UserId;
import com.pointeight.user.domain.UserNotFoundException;
import com.pointeight.user.domain.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssignNextMatchUseCaseTest {

  private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
  private static final long DEFAULT_EXPIRY_SECONDS = 43200;
  private static final int CANDIDATE_POOL_SIZE = 50;

  private UserRepository users;
  private MatchRepository matches;
  private CreateManualMatchUseCase createMatch;
  private CandidateSelectionStrategy strategy;
  private AssignNextMatchUseCase useCase;

  @BeforeEach
  void setUp() {
    users = mock(UserRepository.class);
    matches = mock(MatchRepository.class);
    createMatch = mock(CreateManualMatchUseCase.class);
    strategy = mock(CandidateSelectionStrategy.class);
    useCase =
        new AssignNextMatchUseCase(
            users, matches, createMatch, strategy, RANDOM, DEFAULT_EXPIRY_SECONDS, CANDIDATE_POOL_SIZE);
  }

  private static User userOf(Gender gender, Gender... seeking) {
    return User.register(
        new Profile(30, gender, Set.of(seeking), SeekingType.LONG_TERM, "Madrid", "docente", List.of()),
        null,
        Clock.systemUTC());
  }

  @Test
  void returns_empty_when_the_seeker_already_has_an_open_match() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    when(matches.hasOpenMatch(seeker.id())).thenReturn(true);

    Optional<Match> result = useCase.execute(seeker.id());

    assertThat(result).isEmpty();
    verify(users, never()).findById(any());
  }

  @Test
  void throws_when_the_seeker_does_not_exist() {
    UserId missing = UserId.newId();
    when(matches.hasOpenMatch(missing)).thenReturn(false);
    when(users.findById(missing)).thenReturn(Optional.empty());

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(missing))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void creates_a_match_with_whoever_the_strategy_picks() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User eligible = userOf(Gender.MALE, Gender.FEMALE);
    Match created = mock(Match.class);

    when(matches.hasOpenMatch(seeker.id())).thenReturn(false);
    when(users.findById(seeker.id())).thenReturn(Optional.of(seeker));
    when(users.findAll(0, CANDIDATE_POOL_SIZE)).thenReturn(List.of(eligible));
    when(matches.hasOpenMatch(eligible.id())).thenReturn(false);
    when(strategy.selectFor(eq(seeker), anyList(), eq(RANDOM))).thenReturn(Optional.of(eligible));
    when(createMatch.execute(seeker.id(), eligible.id(), Duration.ofSeconds(DEFAULT_EXPIRY_SECONDS)))
        .thenReturn(created);

    Optional<Match> result = useCase.execute(seeker.id());

    assertThat(result).contains(created);
  }

  @Test
  void returns_empty_when_the_strategy_finds_nobody() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    when(matches.hasOpenMatch(seeker.id())).thenReturn(false);
    when(users.findById(seeker.id())).thenReturn(Optional.of(seeker));
    when(users.findAll(0, CANDIDATE_POOL_SIZE)).thenReturn(List.of());
    when(strategy.selectFor(eq(seeker), anyList(), eq(RANDOM))).thenReturn(Optional.empty());

    Optional<Match> result = useCase.execute(seeker.id());

    assertThat(result).isEmpty();
    verify(createMatch, never()).execute(any(), any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void excludes_candidates_who_already_have_an_open_match_from_the_strategys_pool() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User busyButOtherwiseEligible = userOf(Gender.MALE, Gender.FEMALE);

    when(matches.hasOpenMatch(seeker.id())).thenReturn(false);
    when(users.findById(seeker.id())).thenReturn(Optional.of(seeker));
    when(users.findAll(0, CANDIDATE_POOL_SIZE)).thenReturn(List.of(busyButOtherwiseEligible));
    when(matches.hasOpenMatch(busyButOtherwiseEligible.id())).thenReturn(true);
    when(strategy.selectFor(eq(seeker), anyList(), eq(RANDOM))).thenReturn(Optional.empty());

    useCase.execute(seeker.id());

    ArgumentCaptor<List<User>> poolCaptor = ArgumentCaptor.forClass(List.class);
    verify(strategy).selectFor(eq(seeker), poolCaptor.capture(), eq(RANDOM));
    assertThat(poolCaptor.getValue()).doesNotContain(busyButOtherwiseEligible);
  }

  @Test
  @SuppressWarnings("unchecked")
  void excludes_candidates_without_mutual_gender_interest_from_the_strategys_pool() {
    User seeker = userOf(Gender.FEMALE, Gender.MALE);
    User uninterested = userOf(Gender.MALE, Gender.NON_BINARY);

    when(matches.hasOpenMatch(seeker.id())).thenReturn(false);
    when(users.findById(seeker.id())).thenReturn(Optional.of(seeker));
    when(users.findAll(0, CANDIDATE_POOL_SIZE)).thenReturn(List.of(uninterested));
    when(matches.hasOpenMatch(uninterested.id())).thenReturn(false);
    when(strategy.selectFor(eq(seeker), anyList(), eq(RANDOM))).thenReturn(Optional.empty());

    useCase.execute(seeker.id());

    ArgumentCaptor<List<User>> poolCaptor = ArgumentCaptor.forClass(List.class);
    verify(strategy).selectFor(eq(seeker), poolCaptor.capture(), eq(RANDOM));
    assertThat(poolCaptor.getValue()).isEmpty();
  }
}
