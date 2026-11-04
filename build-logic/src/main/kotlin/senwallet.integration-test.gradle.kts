import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.testing.base.TestingExtension

// Adds an `integrationTest` suite (src/integrationTest) run by `check`. Needs
// Docker: the tests share one PostgreSQL Testcontainer per JVM.
plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")
val testingExtension = extensions.getByType<TestingExtension>()

testingExtension.suites.register<JvmTestSuite>("integrationTest") {
    useJUnitJupiter(libs.findVersion("junit").get().requiredVersion)
    dependencies {
        implementation(project())
    }
    targets.configureEach {
        testTask.configure {
            shouldRunAfter(tasks.named("test"))
            systemProperty("senwallet.root", rootDir.absolutePath)
            maxParallelForks = 1
        }
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.named("testImplementation").get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.named("testRuntimeOnly").get())
}

tasks.named("check") {
    dependsOn(testingExtension.suites.named("integrationTest"))
}
