package com.medroute.nav.dto;

import java.util.List;

public record AdminReleaseListResponse(
    List<AdminReleaseSummary> items,
    String nextPageToken
) {
    public AdminReleaseListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
