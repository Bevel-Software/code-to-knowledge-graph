plugins {
    kotlin("jvm")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}