package com.medroute.nav.navigation.service;

import com.medroute.nav.dto.NavigationContextResponse;
import com.medroute.nav.dto.NavigationPoiSearchResponse;
import com.medroute.nav.navigation.model.FloorSnapshot;
import com.medroute.nav.navigation.model.NavigationGraph;
import com.medroute.nav.navigation.model.PoiSnapshot;
import com.medroute.nav.navigation.repository.PublishedGraphRepository;
import com.medroute.nav.navigation.repository.PublishedGraphSnapshot;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class NavigationContextService {
    private final PublishedGraphRepository graphRepository;

    public NavigationContextService(
        PublishedGraphRepository graphRepository
    ) {
        this.graphRepository = graphRepository;
    }

    public NavigationContextResponse activeContext(UUID buildingId) {
        PublishedGraphSnapshot snapshot = graphRepository.active(buildingId);
        NavigationGraph graph = snapshot.graph();
        List<NavigationContextResponse.Floor> floors = graph.floors().values()
            .stream()
            .sorted(Comparator.comparingInt(FloorSnapshot::levelNo))
            .map(this::floor)
            .toList();

        return new NavigationContextResponse(
            new NavigationContextResponse.Building(
                graph.buildingId(),
                snapshot.buildingCode(),
                snapshot.buildingName()
            ),
            new NavigationContextResponse.Release(
                graph.releaseId(),
                snapshot.releaseCode(),
                snapshot.publishedAt()
            ),
            floors,
            List.of("normal", "accessible")
        );
    }

    public NavigationPoiSearchResponse activePois(
        UUID buildingId,
        String keyword,
        UUID floorId,
        String category
    ) {
        NavigationGraph graph = graphRepository.active(buildingId).graph();
        String normalizedKeyword = normalize(keyword);
        String normalizedCategory = normalize(category);
        List<NavigationPoiSearchResponse.Poi> items = graph.pois().values()
            .stream()
            .filter(poi -> floorId == null || floorId.equals(poi.floorId()))
            .filter(poi -> matchesCategory(poi, normalizedCategory))
            .filter(poi -> matchesKeyword(poi, normalizedKeyword))
            .sorted(poiOrder(graph))
            .map(poi -> poi(graph, poi))
            .toList();

        return new NavigationPoiSearchResponse(
            graph.releaseId(),
            items,
            null
        );
    }

    private NavigationContextResponse.Floor floor(FloorSnapshot floor) {
        return new NavigationContextResponse.Floor(
            floor.id(),
            floor.code(),
            floor.name(),
            floor.levelNo(),
            new NavigationContextResponse.MapRevision(
                floor.mapRevisionId(),
                floor.mapRevisionNo(),
                floor.imageUrl(),
                floor.imageWidth(),
                floor.imageHeight()
            )
        );
    }

    private NavigationPoiSearchResponse.Poi poi(
        NavigationGraph graph,
        PoiSnapshot poi
    ) {
        FloorSnapshot floor = graph.floors().get(poi.floorId());
        if (floor == null) {
            throw new IllegalStateException(
                "POI references a missing floor: " + poi.code()
            );
        }
        return new NavigationPoiSearchResponse.Poi(
            poi.id(),
            poi.code(),
            poi.name(),
            poi.category(),
            poi.floorId(),
            floor.code(),
            poi.x(),
            poi.y(),
            poi.accessible(),
            poi.searchKeywords()
        );
    }

    private Comparator<PoiSnapshot> poiOrder(NavigationGraph graph) {
        return Comparator
            .comparingInt((PoiSnapshot poi) ->
                graph.floors().get(poi.floorId()).levelNo()
            )
            .thenComparing(PoiSnapshot::name);
    }

    private boolean matchesCategory(PoiSnapshot poi, String category) {
        return category.isEmpty()
            || poi.category().equalsIgnoreCase(category);
    }

    private boolean matchesKeyword(PoiSnapshot poi, String keyword) {
        if (keyword.isEmpty()) {
            return true;
        }
        return normalize(poi.name()).contains(keyword)
            || normalize(poi.code()).contains(keyword)
            || poi.searchKeywords().stream()
                .map(this::normalize)
                .anyMatch(value -> value.contains(keyword));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
