/**
 * Feature package "match", organized as Ports &amp; Adapters:
 *
 * <ul>
 *   <li>{@code domain/} — aggregate, Value Objects, events, and the repository port. Pure POJOs,
 *       without a single JPA or Spring annotation.
 *   <li>{@code application/} — use cases: orchestrate domain and ports, oblivious to HTTP.
 *   <li>{@code infrastructure/} — adapters: JPA entities, mappers, controllers, and DTOs.
 * </ul>
 */
package com.pointeight.match;
