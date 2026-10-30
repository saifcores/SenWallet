plugins {
    id("senwallet.java-library")
}

dependencies {
    testImplementation(project(":platform:shared-kernel"))
    testImplementation(project(":contexts:ledger:ledger-domain"))
    testImplementation(project(":contexts:ledger:ledger-application"))
    testImplementation(project(":contexts:ledger:ledger-infrastructure"))
    testImplementation(project(":contexts:ledger:ledger-boot"))
    testImplementation(libs.archunit.junit5)
}
