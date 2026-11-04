plugins {
    id("senwallet.java-library")
}

dependencies {
    api(project(":contexts:ledger:ledger-application"))
    implementation(project(":platform:event-contracts"))
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.amqp)
    runtimeOnly(libs.postgresql)
}
