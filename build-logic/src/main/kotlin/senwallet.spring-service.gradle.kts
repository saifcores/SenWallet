// Deployable Spring Boot service (the *-boot module of a context, simulators).
plugins {
    id("senwallet.java-library")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    implementation(platform(libs.findLibrary("spring-boot-bom").get()))
    implementation(libs.findLibrary("spring-boot-starter").get())
    testImplementation(libs.findLibrary("spring-boot-starter-test").get())
}
