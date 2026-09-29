FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

COPY src src
RUN find src -type f -name '*.java' -exec sed -i 's/\r$//' {} +
RUN ./mvnw -q package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system flashbooking \
    && useradd --system --gid flashbooking --home-dir /app --no-create-home flashbooking

WORKDIR /app
COPY --from=build --chown=flashbooking:flashbooking /workspace/target/flash-booking-*.jar app.jar

USER flashbooking

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
