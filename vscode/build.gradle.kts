import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.TextReportRenderer

// Add buildscript dependency for the Nexus plugin
buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("io.github.gradle-nexus:publish-plugin:2.0.0")
    }
}

plugins {
    kotlin("jvm") version "2.0.10"
    `maven-publish`
    id("com.github.jk1.dependency-license-report") version "2.9"
    signing
}

// Check if this is a standalone project build
if (rootProject.name == "code-to-knowledge-graph-vscode") {
    // Apply the Nexus plugin when built as a standalone project
    apply {
        plugin("io.github.gradle-nexus.publish-plugin")
    }

    // Access the extension directly after applying the plugin
    configure<io.github.gradlenexus.publishplugin.NexusPublishExtension> {
        repositories {
            sonatype {
                nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
                snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            }
        }
    }
}

licenseReport {
    renderers = arrayOf(
        TextReportRenderer()
    )
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
}

// Define group and version based on root project or use defaults for standalone
val projectGroup = "software.bevel"
val projectVersion = "1.2.0"

group = projectGroup
version = projectVersion

repositories {
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.17")
    // Handle external module dependencies differently based on whether we're in standalone or multi-project mode
    if (rootProject.name == "code-to-knowledge-graph-vscode") {
        api("$projectGroup:file-system-domain:1.2.0")
        api("$projectGroup:graph-domain:1.2.0")
    } else {
        api(project(":file-system-domain"))
        api(project(":graph-domain"))
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")

    testImplementation("ch.qos.logback:logback-classic:1.4.14")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.mockito:mockito-core:4.0.0")
    testImplementation("org.mockito:mockito-inline:4.0.0")
    testImplementation("io.mockk:mockk:1.13.16")
}

tasks.test {
    useJUnitPlatform()
}

// Create Javadoc and source jars
java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = projectGroup
            artifactId = "code-to-knowledge-graph-vscode"
            version = projectVersion

            pom {
                name.set("Code to Knowledge Graph")
                description.set("Library for converting code to knowledge graphs")
                url.set("https://bevel.software")

                licenses {
                    license {
                        name.set("Mozilla Public License 2.0")
                        url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                    }
                }

                developers {
                    developer {
                        name.set("Razvan-Ion Radulescu")
                        email.set("razvan.radulescu@bevel.software")
                        organization.set("Bevel Software")
                        organizationUrl.set("https://bevel.software")
                    }
                }

                scm {
                    connection.set("scm:git:https://github.com/Bevel-Software/code-to-knowledge-graph.git")
                    developerConnection.set("scm:git:ssh://git@github.com:Bevel-Software/code-to-knowledge-graph.git")
                    url.set("https://github.com/Bevel-Software/code-to-knowledge-graph")
                }
            }
        }
    }
}

// Configure signing
signing {
    sign(publishing.publications["maven"])
}
