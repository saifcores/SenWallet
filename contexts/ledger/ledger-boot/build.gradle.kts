plugins {
    id("senwallet.spring-service")
    id("senwallet.integration-test")
}

dependencies {
    implementation(project(":contexts:ledger:ledger-infrastructure"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    integrationTestImplementation(project(":platform:test-support"))
    integrationTestImplementation(libs.spring.boot.starter.webmvc.test)
    integrationTestImplementation(libs.swagger.parser)
}

tasks.bootJar {
    archiveFileName = "ledger.jar"
}
