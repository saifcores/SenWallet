# Built from the boot jar: ./gradlew :simulators:pispi:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 pispi

WORKDIR /app
COPY build/libs/pispi.jar pispi.jar
USER pispi

EXPOSE 9101
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/pispi.jar"]
