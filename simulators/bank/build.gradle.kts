plugins {
    id("senwallet.spring-service")
    id("senwallet.integration-test")
}

dependencies {
    implementation(project(":simulators:common"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.aws.s3)

    integrationTestImplementation(platform(libs.testcontainers.bom))
    integrationTestImplementation(libs.spring.boot.starter.webmvc.test)
    integrationTestImplementation(libs.testcontainers.junit.jupiter)
}

tasks.bootJar {
    archiveFileName = "bank.jar"
}
