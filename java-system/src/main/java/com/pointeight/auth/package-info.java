/**
 * Feature package "auth", organized as Ports &amp; Adapters like every other feature:
 *
 * <ul>
 *   <li>{@code domain/} — the {@code Account} entity, Value Objects, and the repository port.
 *       Pure POJOs, without a single JPA or Spring annotation.
 *   <li>{@code application/} — use cases: registration (orchestrated together with {@code user}'s
 *       own registration use case) and login.
 *   <li>{@code infrastructure/} — JPA entity/mapper/repository, the JWT issuer and validation
 *       filter, and the controller.
 * </ul>
 *
 * <p>Deliberately separate from {@code user}: login identity and the dating profile that gets
 * matched have different lifecycles (DEC-022), the same way Layer 1 and Layer 2 are kept apart
 * inside {@code User} itself.
 */
package com.pointeight.auth;
