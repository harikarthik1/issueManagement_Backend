package com.issuemanagement.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageableUtilsTest {

    @Test
    void sanitize_shouldStripBracketsAndQuotesFromSwaggerUiSort() {
        Sort dirtySort = Sort.by(Sort.Direction.ASC, "[\"createdAt\"]");
        Pageable pageable = PageRequest.of(0, 10, dirtySort);

        Pageable result = PageableUtils.sanitize(pageable);

        assertThat(result.getSort().isSorted()).isTrue();
        Sort.Order order = result.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getProperty()).isEqualTo("createdAt");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void sanitize_shouldPreserveValidSort() {
        Sort validSort = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(1, 20, validSort);

        Pageable result = PageableUtils.sanitize(pageable);

        assertThat(result.getSort().isSorted()).isTrue();
        Sort.Order order = result.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getProperty()).isEqualTo("createdAt");
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sanitize_shouldHandleUnsorted() {
        Pageable pageable = PageRequest.of(0, 10);

        Pageable result = PageableUtils.sanitize(pageable);

        assertThat(result.getSort().isUnsorted()).isTrue();
    }
}
