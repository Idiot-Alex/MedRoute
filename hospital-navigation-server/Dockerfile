FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system medroute \
    && useradd --system --gid medroute --home-dir /app medroute
WORKDIR /app
COPY --from=build --chown=medroute:medroute \
    /workspace/target/hospital-navigation-server-*.jar \
    /app/medroute-server.jar

USER medroute
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/medroute-server.jar"]
