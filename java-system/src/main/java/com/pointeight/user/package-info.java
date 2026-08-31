/**
 * Feature package "user", organizado en Ports & Adapters:
 *
 * <ul>
 *   <li>{@code domain/} — aggregate, Value Objects, eventos y el port del repositorio. POJOs puros,
 *       sin una sola anotación de JPA ni de Spring.
 *   <li>{@code application/} — casos de uso: orquestan dominio y ports, sin saber de HTTP.
 *   <li>{@code infrastructure/} — adapters: entities JPA, mappers, controllers y DTOs.
 * </ul>
 */
package com.pointeight.user;
