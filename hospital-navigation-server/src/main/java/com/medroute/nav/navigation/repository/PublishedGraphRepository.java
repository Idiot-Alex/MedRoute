package com.medroute.nav.navigation.repository;

import java.util.UUID;

public interface PublishedGraphRepository {
    PublishedGraphSnapshot active(UUID buildingId);

    PublishedGraphSnapshot published(UUID releaseId);
}
