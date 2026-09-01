package com.pointeight.shared.infrastructure;

import java.util.List;

/** Pagination wrapper common to every listing. */
public record PageResponse<T>(List<T> content, int page, int size, long total) {

  public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
    return new PageResponse<>(content, page, size, total);
  }
}
