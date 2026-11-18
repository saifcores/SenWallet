plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:payments:payments-domain"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.tx)
    implementation("org.slf4j:slf4j-api")
}
