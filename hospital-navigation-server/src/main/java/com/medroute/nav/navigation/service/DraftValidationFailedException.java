package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.AdminValidationResponse;

public class DraftValidationFailedException extends RuntimeException {
    private final AdminValidationResponse validation;

    public DraftValidationFailedException(
        AdminValidationResponse validation
    ) {
        super("Draft has not passed validation");
        this.validation = validation;
    }

    public AdminValidationResponse validation() {
        return validation;
    }
}
