plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:wallet:wallet-application"))
    implementation(project(":platform:ledger-client"))
    implementation(project(":platform:event-contracts"))
    implementation(project(":platform:event-inbox"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.amqp)
    runtimeOnly(libs.postgresql)
}
