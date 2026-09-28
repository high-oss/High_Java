// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

plugins {
    `java-library`
    `maven-publish`
}

group = "live.high"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
}

repositories {
    mavenCentral()
}

// Generated model classes (OpenAPI Generator, java/native) live in their own
// source directory, are committed, and are never hand-edited. See
// generated/README.md and scripts/regenerate.sh for how to refresh them.
sourceSets {
    main {
        java.srcDirs("src/main/java", "generated/src/main/java")
        resources.srcDirs("src/main/resources")
    }
    test {
        java.srcDirs("src/test/java")
        resources.srcDirs("src/test/resources")
    }
}

val jacksonVersion = "2.21.5"
val jacksonAnnotationsVersion = "2.21"

dependencies {
    // The generated models (OpenAPI Generator, java/native library) are Jackson
    // POJOs. java.net.http ships no JSON support of its own (unlike Node's
    // global JSON), so Jackson is the SDK's one runtime dependency, carried
    // over from the generator's own choice of serialization library.
    api("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
    api("com.fasterxml.jackson.core:jackson-annotations:$jacksonAnnotationsVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("org.openapitools:jackson-databind-nullable:0.2.11")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    if (name == "compileJava") {
        options.compilerArgs.add("-Xlint:deprecation")
    }
    // Generated sources are OpenAPI Generator output and are exempt from our
    // own warning discipline.
}

tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "high-openapi",
            "Implementation-Version" to project.version,
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "live.high"
            artifactId = "high-openapi"
            version = project.version.toString()
            from(components["java"])

            pom {
                name.set("high-openapi")
                description.set("Official Java SDK for the HIGH Open API.")
                url.set("https://github.com/high-oss/High_Java")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("high-oss")
                        name.set("High-OSS")
                        email.set("care@high.live")
                    }
                }
                scm {
                    url.set("https://github.com/high-oss/High_Java")
                    connection.set("scm:git:https://github.com/high-oss/High_Java.git")
                    developerConnection.set("scm:git:https://github.com/high-oss/High_Java.git")
                }
            }
        }
    }
}
