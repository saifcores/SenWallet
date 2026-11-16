plugins {
    id("senwallet.spring-service")
    id("senwallet.integration-test")
}

dependencies {
    implementation(project(":simulators:common"))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)

    integrationTestImplementation(libs.spring.boot.starter.webmvc.test)
    integrationTestImplementation(libs.okhttp.mockwebserver)
}

tasks.bootJar {
    archiveFileName = "pispi.jar"
}
