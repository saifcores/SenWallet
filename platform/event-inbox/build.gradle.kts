plugins {
    id("senwallet.java-library")
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.jdbc)
}
