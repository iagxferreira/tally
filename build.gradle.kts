import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    id("net.ltgt.errorprone") version "5.1.1"
}

group = "tally"
version = "0.1.0"

java {
    toolchain {
        // Must match the java pin in mise.toml. mise decides which JVM runs
        // Gradle; this decides what the code compiles against. If they drift,
        // Gradle goes looking for a JDK it cannot find.
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // The domain's only production dependency, and only because
    // java.util.UUID cannot mint a v7: randomUUID() is v4 only, and the JDK
    // has no v7 factory as of Java 25. See ADR 003.
    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    errorprone("com.google.errorprone:error_prone_core:2.50.0")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.3")
    // Replaces the Cargo lint policy lost in the Rust -> Java move: no
    // floating point in the domain, and no domain dependency on core.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}
