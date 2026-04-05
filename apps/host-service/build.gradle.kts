plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.12")
    implementation("io.ktor:ktor-serialization-gson-jvm:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.8")
}

application {
    mainClass.set("com.ktv.host.app.HostApplicationKt")
}

kotlin {
    jvmToolchain(17)
}
