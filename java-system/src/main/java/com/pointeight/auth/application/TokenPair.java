package com.pointeight.auth.application;

/** What login and refresh both hand back: a short-lived access token and its refresh token. */
public record TokenPair(String accessToken, String refreshToken) {}
