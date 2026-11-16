plugins {
    id("senwallet.java-library")
}

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.spring.boot.starter.webmvc)
    api(libs.spring.boot.starter.actuator)
    api(libs.spring.boot.starter)
    api(libs.jackson.databind)
}
