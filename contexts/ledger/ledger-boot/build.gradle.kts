plugins {
    id("senwallet.spring-service")
}

dependencies {
    implementation(project(":contexts:ledger:ledger-infrastructure"))
}
