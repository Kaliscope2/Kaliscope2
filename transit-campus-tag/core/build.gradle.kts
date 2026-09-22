plugins {
    kotlin("jvm") version "1.9.24"
}

group = "nz.kaliscope.tagdemo"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
