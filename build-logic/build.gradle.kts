plugins {
    `kotlin-dsl`
}

// The Java language server imports this included build with a different
// Gradle than the wrapper. Separate output dirs so the two compilers
// cannot share incremental caches or emit an empty plugin jar.
layout.buildDirectory.set(
    layout.projectDirectory.dir("build-${gradle.gradleVersion}"),
)

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
}
