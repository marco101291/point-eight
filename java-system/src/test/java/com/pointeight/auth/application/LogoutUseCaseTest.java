package com.pointeight.auth.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pointeight.auth.domain.RefreshToken;
import com.pointeight.auth.domain.RefreshTokenRepository;
import com.pointeight.user.domain.UserId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LogoutUseCaseTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final LogoutUseCase useCase = new LogoutUseCase(refreshTokens);

  @Test
  void deletes_the_whole_family_the_token_belongs_to() {
    RefreshToken.Issued issued =
        RefreshToken.issueNewFamily(UserId.newId(), Duration.ofDays(30), CLOCK);
    when(refreshTokens.findByTokenHash(issued.token().tokenHash()))
        .thenReturn(Optional.of(issued.token()));

    useCase.execute(issued.rawValue());

    verify(refreshTokens).deleteFamily(issued.token().familyId());
  }

  @Test
  void an_unknown_token_is_a_silent_no_op() {
    when(refreshTokens.findByTokenHash(RefreshToken.hash("garbage"))).thenReturn(Optional.empty());

    useCase.execute("garbage");

    verify(refreshTokens, never()).deleteFamily(any());
  }
}
