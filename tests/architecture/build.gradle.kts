plugins {
    id("senwallet.java-library")
}

dependencies {
    testImplementation(project(":platform:shared-kernel"))
    testImplementation(project(":contexts:ledger:ledger-domain"))
    testImplementation(project(":contexts:ledger:ledger-application"))
    testImplementation(project(":contexts:ledger:ledger-infrastructure"))
    testImplementation(project(":contexts:ledger:ledger-boot"))
    testImplementation(project(":platform:ledger-client"))
    testImplementation(project(":platform:event-contracts"))
    testImplementation(project(":platform:event-inbox"))
    testImplementation(project(":contexts:wallet:wallet-domain"))
    testImplementation(project(":contexts:wallet:wallet-application"))
    testImplementation(project(":contexts:wallet:wallet-infrastructure"))
    testImplementation(project(":contexts:wallet:wallet-boot"))
    testImplementation(libs.archunit.junit5)
}
