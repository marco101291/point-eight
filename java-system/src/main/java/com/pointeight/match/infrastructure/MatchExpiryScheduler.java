package com.pointeight.match.infrastructure;

import com.pointeight.match.application.MatchLifecycleUseCases;
import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.match.domain.MatchStatus;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code Match.isDue()}'s own javadoc has claimed "checked by the scheduler from M4 on" since M1
 * — this is that scheduler, finally built. Without it, a match past its {@code expiresAt} just
 * sat {@code ACTIVE} until something else happened to touch it; found the hard way, as a reveal
 * screen showing a countdown already stuck at zero.
 *
 * <p>Reuses {@link MatchLifecycleUseCases#expire}, not a bespoke bulk update: the existing {@code
 * MatchExpiredEventListener} chain (rematching both users) fires exactly the way it already does
 * for a manual {@code /expire} call. This job's only job is finding what's due.
 *
 * <p>Fetches every {@code ACTIVE} match in one page rather than paging with an incrementing
 * offset: expiring a match removes it from the {@code ACTIVE} set mid-sweep, which would shift
 * what "page 2" means out from under an offset-based loop. Same "fine at this scale" tradeoff
 * {@code AssignNextMatchUseCase}'s own candidate-pool fetch already accepts.
 */
@Component
public class MatchExpiryScheduler {

  private static final Logger log = LoggerFactory.getLogger(MatchExpiryScheduler.class);

  private final MatchRepository matches;
  private final MatchLifecycleUseCases lifecycle;
  private final Clock clock;

  public MatchExpiryScheduler(
      MatchRepository matches, MatchLifecycleUseCases lifecycle, Clock clock) {
    this.matches = matches;
    this.lifecycle = lifecycle;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${pointeight.match.expiry-check-interval-ms:60000}")
  public void expireDueMatches() {
    long activeCount = matches.count(MatchStatus.ACTIVE);
    if (activeCount == 0) {
      return;
    }
    List<Match> active = matches.findAll(MatchStatus.ACTIVE, 0, Math.toIntExact(activeCount));
    for (Match match : active) {
      if (match.isDue(clock)) {
        tryExpire(match);
      }
    }
  }

  private void tryExpire(Match match) {
    try {
      lifecycle.expire(match.id());
    } catch (RuntimeException e) {
      log.warn("Could not expire match {}: {}", match.id(), e.getMessage());
    }
  }
}
