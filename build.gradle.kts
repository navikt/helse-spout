plugins {
    kotlin("jvm") version "2.4.0"
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("no.nav.sykepenger.libs:logging:20260829.1737")
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    implementation(platform("io.ktor:ktor-bom:3.5.1"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")

    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.1"))
    implementation(platform("tools.jackson:jackson-bom:3.2.1"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("org.apache.kafka:kafka-clients:4.3.1")

    testImplementation(platform("org.junit:junit-bom:6.1.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("25"))
    }
}

tasks {

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<Jar> {
        archiveBaseName.set("app")

        manifest {
            attributes["Main-Class"] = "no.nav.helse.spout.AppKt"
            attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
                it.name
            }
        }

        doLast {
            configurations.runtimeClasspath.get().forEach {
                val file = File("${layout.buildDirectory.get()}/libs/${it.name}")
                if (!file.exists())
                    it.copyTo(file)
            }
        }
    }
}
