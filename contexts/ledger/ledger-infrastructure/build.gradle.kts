plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:ledger:ledger-application"))
    implementation(platform(libs.spring.boot.bom))
}
