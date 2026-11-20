package org.example.Service;

import java.util.List;

/** One page of a larger result. {@code page} is 1-based. */
public record Page<T>(List<T> items, int page, int pageSize, long totalItems) {

    public int totalPages() {
        return pageSize <= 0 ? 1 : (int) Math.max(1, (totalItems + pageSize - 1) / pageSize);
    }
}
