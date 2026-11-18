# Built from the boot jar: ./gradlew :contexts:payments:payments-boot:bootJar
FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 payments

WORKDIR /app
COPY build/libs/payments.jar payments.jar
USER payments

EXPOSE 8003
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/payments.jar"]
