// Shared test infrastructure: one PostgreSQL 16 container per test JVM.
plugins {
    id("senwallet.java-library")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.testcontainers.postgresql)
    runtimeOnly(libs.postgresql)
}
