package com.pointeight.simulation.infrastructure;

/**
 * Consumed from {@code compatibility.score.computed}. Engine -> Java, some time after the
 * matching request — {@code matchId} is what ties this back to the match that asked.
 */
record CompatibilityScoreResponseMessage(
    String matchId, String modelVersion, double compatibilityScore, int expiryDays) {}
