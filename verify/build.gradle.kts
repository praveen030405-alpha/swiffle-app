plugins {
    kotlin("jvm") version "2.3.20"
    application
}
repositories { mavenCentral() }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") }
kotlin {
    jvmToolchain(17)
    sourceSets.main {
        kotlin.srcDir("../app/src/main/java/com/swiffle/app/conversion/proof/verify")
    }
}
application { mainClass.set("com.swiffle.app.conversion.proof.verify.VerifyBundle") }
