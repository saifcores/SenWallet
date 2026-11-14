# Built from the boot jar: ./gradlew :simulators:mno:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 mno

WORKDIR /app
COPY build/libs/mno.jar mno.jar
USER mno

EXPOSE 9102
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/mno.jar"]
