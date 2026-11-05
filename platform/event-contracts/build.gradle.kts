plugins {
    id("senwallet.java-library")
}

dependencies {
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.jackson.databind)
}
