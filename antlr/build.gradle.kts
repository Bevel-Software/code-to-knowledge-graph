plugins {
    kotlin("jvm")
    antlr
}

tasks.generateGrammarSource {
    outputDirectory = file("${project.projectDir}/src/main/java/software/bevel/code_to_knowledge_graph/antlr/")

    // pass -package to make generator put code in not default space
    arguments = listOf("-package", "software.bevel.code_to_knowledge_graph.antlr")

    source = fileTree("src/main/antlr")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

dependencies {
    antlr("org.antlr:antlr4:4.9.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}