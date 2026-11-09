# Built from the boot jar: ./gradlew :contexts:wallet:wallet-boot:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 wallet

WORKDIR /app
COPY build/libs/wallet.jar wallet.jar
USER wallet

EXPOSE 8001
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/wallet.jar"]
