package com.pointeight.match.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Match manual entre dos IDs dados. {@code expiryDurationSeconds} es opcional: si no viene, se usa
 * {@code pointeight.match.default-expiry-seconds}.
 */
public record CreateMatchRequest(
    @NotBlank String userAId,
    @NotBlank String userBId,
    @Positive Long expiryDurationSeconds) {}
