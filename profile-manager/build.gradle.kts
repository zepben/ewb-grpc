plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("application")
}

group = "com.zepben"
version = "1.0-SNAPSHOT"

application {
    mainClass = "com.zepben.profilemanager.AppKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.sf.ucanaccess:ucanaccess:5.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("com.charleskorn.kaml:kaml:0.57.0")
    implementation("com.github.ajalt.clikt:clikt:4.3.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(11)
}

tasks {
    jar {
        manifest {
            attributes(
                "Main-Class" to "com.zepben.profilemanager.AppKt"
            )
        }
    }
}