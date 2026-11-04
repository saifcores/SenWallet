# Built from the boot jar: ./gradlew :contexts:ledger:ledger-boot:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 ledger

WORKDIR /app
COPY build/libs/ledger.jar ledger.jar
USER ledger

EXPOSE 8002
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/ledger.jar"]
