import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.render.TextReportRenderer
import com.github.jk1.license.filter.LicenseBundleNormalizer

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
if (rootProject.name == "code-to-knowledge-graph") {
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
val projectVersion = "1.1.2"

group = projectGroup
version = projectVersion

repositories {
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.17")
    // Handle external module dependencies differently based on whether we're in standalone or multi-project mode
    if (rootProject.name == "code-to-knowledge-graph") {
        //api(project(":antlr"))
        //api(project(":regex"))
        api(project(":providers"))
        api(project(":vscode"))
        api("$projectGroup:file-system-domain:1.1.0")
        api("$projectGroup:graph-domain:1.1.0")
        api("$projectGroup:networking:1.1.0")
    } else {
        //api(project(":code-to-knowledge-graph:antlr"))
        //api(project(":code-to-knowledge-graph:regex"))
        api(project(":code-to-knowledge-graph:providers"))
        api(project(":code-to-knowledge-graph:vscode"))
        api(project(":file-system-domain"))
        api(project(":graph-domain"))
        api(project(":networking"))
    }
}

// Create Javadoc and source jars
java {
    withJavadocJar()
    withSourcesJar()
}

// Configure main jar to include classes from subprojects
tasks.named<Jar>("jar") {
    // Include classes from submodules
    if (rootProject.name == "code-to-knowledge-graph") {
        // For standalone mode
        project(":providers").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
        project(":regex").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
        project(":vscode").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
    } else {
        // For multi-project mode
        project(":code-to-knowledge-graph:providers").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
        project(":code-to-knowledge-graph:regex").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
        project(":code-to-knowledge-graph:vscode").let { subproject ->
            from(subproject.sourceSets.main.get().output)
        }
    }
    
    // Avoid duplicate files issues
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            groupId = projectGroup
            artifactId = "code-to-knowledge-graph"
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

subprojects {
    group = projectGroup
    version = projectVersion

    repositories {
        mavenCentral()
    }

    // Apply Kotlin JVM plugin to all subprojects if they don't have it already
    afterEvaluate {
        if (!plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
        }
        
        // Move dependencies inside afterEvaluate to ensure plugins are applied first
        dependencies {
            testImplementation(kotlin("test"))
            implementation("org.slf4j:slf4j-api:2.0.17")
            
            // Handle external module dependencies differently based on whether we're in standalone or multi-project mode
            if (rootProject.name == "code-to-knowledge-graph") {
                // In standalone mode, use the published Maven artifacts
                api("$projectGroup:file-system-domain:1.1.0")
                api("$projectGroup:graph-domain:1.1.0")
            } else {
                // In multi-project mode, use the project dependencies
                api(project(":file-system-domain"))
                api(project(":graph-domain"))
            }
            
            implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
            implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
            implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.15.2")
        }
    }
}