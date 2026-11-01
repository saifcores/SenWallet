// Adds an `integrationTest` suite (src/integrationTest) run by `check`. Needs
// Docker: the tests share one PostgreSQL Testcontainer per JVM.
plugins {
    java
}

val libs = the<VersionCatalogsExtension>().named("libs")

testing {
    suites {
        register<JvmTestSuite>("integrationTest") {
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
    }
}

configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}
