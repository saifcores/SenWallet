plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:ledger:ledger-domain"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.tx)
}
