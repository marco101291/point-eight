package com.pointeight.match.application;

import com.pointeight.user.domain.Profile;
import java.time.Instant;

/**
 * What {@link RevealActiveMatchUseCase} hands back: the counterpart's profile, plus when the
 * match itself expires. The mobile client's countdown needs the latter; the Layer 1 non-leak
 * guarantee only ever concerned the former.
 */
public record ActiveMatchReveal(Profile profile, Instant expiresAt) {}
