/**
 * Feature package "push". Added in M7, following Ports &amp; Adapters: {@code domain/} (the
 * {@code PushNotificationSender} port), {@code infrastructure/} (the Expo adapter). No
 * {@code application/} of its own — the use case that triggers a send lives in {@code match},
 * since a push notification is always a reaction to something that happened there.
 */
package com.pointeight.push;
