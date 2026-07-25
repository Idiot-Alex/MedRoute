package com.medroute.nav.navigation.service;

import com.medroute.nav.model.EdgeDirection;
import com.medroute.nav.navigation.model.AccessScope;
import com.medroute.nav.navigation.model.ArcType;
import com.medroute.nav.navigation.model.GraphArc;
import com.medroute.nav.navigation.model.VerticalConnector;
import com.medroute.nav.navigation.model.VerticalLink;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryPublishedGraphServiceTest {
    @Test
    void connectorScopeCannotBeRelaxedByAPublicLink() {
        GraphArc arc = arc(AccessScope.STAFF, AccessScope.PUBLIC);

        assertEquals(AccessScope.STAFF, arc.accessScope());
    }

    @Test
    void linkScopeCannotBeRelaxedByAPublicConnector() {
        GraphArc arc = arc(AccessScope.PUBLIC, AccessScope.STAFF);

        assertEquals(AccessScope.STAFF, arc.accessScope());
    }

    private GraphArc arc(
        AccessScope connectorScope,
        AccessScope linkScope
    ) {
        UUID connectorId = UUID.randomUUID();
        VerticalConnector connector = new VerticalConnector(
            connectorId,
            "ELEV-STAFF",
            "员工电梯",
            ArcType.ELEVATOR,
            connectorScope,
            true,
            true
        );
        VerticalLink link = new VerticalLink(
            UUID.randomUUID(),
            "VERT-STAFF-1F-2F",
            connectorId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            30,
            BigDecimal.ZERO,
            EdgeDirection.BOTH,
            linkScope,
            true,
            true
        );

        return InMemoryPublishedGraphService.verticalArc(
            link,
            connector,
            UUID.randomUUID(),
            UUID.randomUUID()
        );
    }
}
