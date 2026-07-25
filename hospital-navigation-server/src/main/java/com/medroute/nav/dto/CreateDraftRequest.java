package com.medroute.nav.dto;

import java.util.UUID;

public record CreateDraftRequest(
    String code,
    UUID basedOnReleaseId,
    String description
) {
}
