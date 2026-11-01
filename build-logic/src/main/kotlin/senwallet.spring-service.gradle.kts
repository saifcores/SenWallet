// Deployable Spring Boot service (the *-boot module of a context, simulators).
plugins {
    id("senwallet.java-library")
    id("org.springframework.boot")
}

val libs = the<VersionCatalogsExtension>().named("libs")

dependencies {
    add("implementation", platform(libs.findLibrary("spring-boot-bom").get()))
    add("implementation", libs.findLibrary("spring-boot-starter").get())
    add("testImplementation", libs.findLibrary("spring-boot-starter-test").get())
}
