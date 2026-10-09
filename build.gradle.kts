plugins {
    alias(libs.plugins.sykepenger.deployable)
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spout.AppKt"
}

dependencies {
    implementation(libs.sykepengerLibs.logging)
    implementation(libs.logback.syslog4j)

    implementation(libs.bundles.ktor.server)

    implementation(libs.jackson.module.kotlin)

    implementation(libs.kafka.clients)
    implementation(libs.opentelemetry.api)

    testImplementation(libs.ktor.server.test.host)
}
