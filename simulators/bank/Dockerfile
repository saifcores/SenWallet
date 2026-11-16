# Built from the boot jar: ./gradlew :simulators:bank:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 bank

WORKDIR /app
COPY build/libs/bank.jar bank.jar
USER bank

EXPOSE 9104
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/bank.jar"]
