plugins {
    kotlin("jvm")
}

dependencies {
    if (rootProject.name == "code-to-knowledge-graph") {
        implementation(project(":providers"))
    } else {
        implementation(project(":code-to-knowledge-graph:providers"))
    }
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}