plugins {
    kotlin("jvm")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}

// Move dependencies inside afterEvaluate to ensure plugins are applied first
dependencies {
    testImplementation(kotlin("test"))
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Handle external module dependencies differently based on whether we're in standalone or multi-project mode
    if (rootProject.name == "code-to-knowledge-graph") {
        // In standalone mode, use the published Maven artifacts
        api("software.bevel:file-system-domain:1.1.0")
        api("software.bevel:graph-domain:1.1.0")
    } else {
        // In multi-project mode, use the project dependencies
        api(project(":file-system-domain"))
        api(project(":graph-domain"))
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
}