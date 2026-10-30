pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "senwallet"

include(
    ":platform:shared-kernel",
    ":contexts:ledger:ledger-domain",
    ":contexts:ledger:ledger-application",
    ":contexts:ledger:ledger-infrastructure",
    ":contexts:ledger:ledger-boot",
    ":tests:architecture",
)
