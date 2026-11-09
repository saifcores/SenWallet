plugins {
    id("senwallet.spring-service")
    id("senwallet.integration-test")
}

dependencies {
    implementation(project(":contexts:wallet:wallet-infrastructure"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    integrationTestImplementation(project(":contexts:ledger:ledger-boot"))
    integrationTestImplementation(project(":platform:test-support"))
    integrationTestImplementation(libs.spring.boot.starter.webmvc.test)
    integrationTestImplementation(libs.swagger.parser)
}

tasks.bootJar {
    archiveFileName = "wallet.jar"
}
