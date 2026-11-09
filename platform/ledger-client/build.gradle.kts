plugins {
    id("senwallet.java-library")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework:spring-web")
    implementation(libs.jackson.databind)
    implementation(libs.resilience4j.retry)
    implementation(libs.resilience4j.timelimiter)
}
