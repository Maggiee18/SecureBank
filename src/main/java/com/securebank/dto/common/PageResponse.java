package com.securebank.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination envelope. Returning Spring's Page directly would tie the public API
 * to Spring's internal JSON shape, which has changed between versions.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        String sort) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.getSort().isSorted() ? page.getSort().toString() : null);
    }
}
