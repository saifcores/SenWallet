import org.gradle.api.plugins.JavaPluginExtension

// Plain Java module: no Spring. Used by every domain, application and kernel module.
plugins {
    `java-library`
    jacoco
}

val libs = the<VersionCatalogsExtension>().named("libs")

extensions.configure<JavaPluginExtension> {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

dependencies {
    add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("assertj-core").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = libs.findVersion("java").get().requiredVersion.toInt()
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Werror", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        xml.required = true
        html.required = true
    }
}
