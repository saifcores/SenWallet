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
    ":platform:event-contracts",
    ":platform:event-inbox",
    ":platform:ledger-client",
    ":platform:test-support",
    ":contexts:ledger:ledger-domain",
    ":contexts:ledger:ledger-application",
    ":contexts:ledger:ledger-infrastructure",
    ":contexts:ledger:ledger-boot",
    ":contexts:wallet:wallet-domain",
    ":contexts:wallet:wallet-application",
    ":contexts:wallet:wallet-infrastructure",
    ":contexts:wallet:wallet-boot",
    ":tests:architecture",
)
