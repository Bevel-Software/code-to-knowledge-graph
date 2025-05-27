plugins {
    kotlin("jvm")
}

dependencies {
    implementation("com.dynatrace.hash4j:hash4j:0.21.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}