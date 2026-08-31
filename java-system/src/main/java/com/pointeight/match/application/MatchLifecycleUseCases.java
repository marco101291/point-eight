package com.pointeight.match.application;

import com.pointeight.match.domain.Match;
import com.pointeight.match.domain.MatchId;
import com.pointeight.match.domain.MatchNotFoundException;
import com.pointeight.match.domain.MatchRepository;
import com.pointeight.shared.domain.DomainEventPublisher;
import java.time.Clock;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las tres transiciones del ciclo de vida. Comparten exactamente la misma coreografía — cargar,
 * transicionar, guardar, publicar — así que se expresa una sola vez y cada operación aporta nada
 * más que el método del aggregate a invocar.
 *
 * <p>Las reglas de qué transición es legal no están acá: viven en {@code MatchStatus}.
 */
@Service
public class MatchLifecycleUseCases {

  private final MatchRepository matches;
  private final DomainEventPublisher events;
  private final Clock clock;

  public MatchLifecycleUseCases(
      MatchRepository matches, DomainEventPublisher events, Clock clock) {
    this.matches = matches;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public Match activate(MatchId id) {
    return apply(id, Match::activate);
  }

  @Transactional
  public Match expire(MatchId id) {
    return apply(id, Match::expire);
  }

  @Transactional
  public Match reject(MatchId id) {
    return apply(id, Match::reject);
  }

  private Match apply(MatchId id, BiConsumer<Match, Clock> transition) {
    Match match = matches.findById(id).orElseThrow(() -> new MatchNotFoundException(id));
    transition.accept(match, clock);
    Match saved = matches.save(match);
    events.publishAll(match.pullEvents());
    return saved;
  }
}
