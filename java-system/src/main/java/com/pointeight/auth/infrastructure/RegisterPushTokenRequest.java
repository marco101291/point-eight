package com.pointeight.auth.infrastructure;

import jakarta.validation.constraints.NotBlank;

public record RegisterPushTokenRequest(@NotBlank String pushToken) {}
