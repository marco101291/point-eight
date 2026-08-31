package com.pointeight.shared.infrastructure;

import java.util.List;

/** Envoltorio de paginación común a todos los listados. */
public record PageResponse<T>(List<T> content, int page, int size, long total) {

  public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
    return new PageResponse<>(content, page, size, total);
  }
}
