package com.pointeight.match.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Manual match between two given IDs. {@code expiryDurationSeconds} is optional: if it's not
 * provided, {@code pointeight.match.default-expiry-seconds} is used.
 */
public record CreateMatchRequest(
    @NotBlank String userAId,
    @NotBlank String userBId,
    @Positive Long expiryDurationSeconds) {}
