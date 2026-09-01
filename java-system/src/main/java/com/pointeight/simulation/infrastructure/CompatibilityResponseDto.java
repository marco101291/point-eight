package com.pointeight.simulation.infrastructure;

record CompatibilityResponseDto(String modelVersion, double compatibilityScore, int expiryDays) {}
