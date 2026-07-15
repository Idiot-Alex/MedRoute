# Hospital Navigation Server

Spring Boot API skeleton for phase two of MedRoute.

This first backend iteration intentionally uses in-memory demo data that mirrors `hospital-map-demo/graph-data.js`. The goal is to validate API shape and route-service flow before introducing PostgreSQL/PostGIS.

## API

- `GET /api/hospitals`
- `GET /api/hospitals/{hospitalId}/floors/{floorId}/map`
- `GET /api/hospitals/{hospitalId}/floors/{floorId}/graph`
- `GET /api/hospitals/{hospitalId}/pois?keyword=药房`
- `POST /api/routes`

Example route request:

```json
{
  "hospitalId": 1,
  "startPoiId": "P1",
  "endPoiId": "P6",
  "routeMode": "accessible"
}
```

## Run

This project is a standard Maven/Spring Boot module:

```bash
cd hospital-navigation-server
mvn spring-boot:run
```

The local environment used for this iteration has Java 17 but does not have Maven installed, so only dependency-free Java smoke checks were run here.

## Verified Locally

Compile and run the dependency-free route algorithm smoke test:

```bash
mkdir -p /tmp/medroute-smoke
javac -d /tmp/medroute-smoke \
  src/main/java/com/medroute/nav/model/PathNode.java \
  src/main/java/com/medroute/nav/model/PathEdge.java \
  src/main/java/com/medroute/nav/algorithm/AStarPathFinder.java \
  src/test/java/com/medroute/nav/algorithm/AStarPathFinderSmoke.java
java -cp /tmp/medroute-smoke com.medroute.nav.algorithm.AStarPathFinderSmoke
```

Expected result: normal and accessible routes both resolve, and the accessible route avoids the non-accessible edge.
