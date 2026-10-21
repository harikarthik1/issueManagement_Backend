package com.issuemanagement.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

public final class PageableUtils {

    private PageableUtils() {}

    /**
     * Sanitizes Pageable sort property names by removing quotes, brackets, and leading/trailing whitespace.
     * This prevents errors when Swagger UI or clients send JSON array formatted sorts like ["createdAt"].
     */
    public static Pageable sanitize(Pageable pageable) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            return pageable;
        }

        List<Sort.Order> sanitizedOrders = new ArrayList<>();
        for (Sort.Order order : pageable.getSort()) {
            String property = order.getProperty();
            if (property != null) {
                String cleaned = property.replaceAll("[\\[\\]\"']", "").trim();
                if (!cleaned.isEmpty()) {
                    sanitizedOrders.add(new Sort.Order(order.getDirection(), cleaned));
                }
            }
        }

        if (sanitizedOrders.isEmpty()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(sanitizedOrders));
    }
}
