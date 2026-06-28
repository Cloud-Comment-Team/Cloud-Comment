package com.cloudcomment.shared.web;

import java.util.List;

public record PaginatedResponse<T>(
    List<T> items,
    int page,
    int pageSize,
    long totalItems,
    int totalPages
) {

    public PaginatedResponse {
        items = List.copyOf(items);
    }

    public static <T> PaginatedResponse<T> of(
        List<T> items,
        int page,
        int pageSize,
        long totalItems
    ) {
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) totalItems / pageSize);
        return new PaginatedResponse<>(items, page, pageSize, totalItems, totalPages);
    }
}
