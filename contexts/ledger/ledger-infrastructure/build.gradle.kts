plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:ledger:ledger-application"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    runtimeOnly(libs.postgresql)
}
