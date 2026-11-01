plugins {
    id("senwallet.java-library")
    `java-test-fixtures`
}

dependencies {
    api(project(":platform:shared-kernel"))
    testImplementation(libs.jqwik)
}
