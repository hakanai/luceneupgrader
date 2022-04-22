
version = "0.5.2-SNAPSHOT"
group = "org.trypticon.luceneupgrader"
description = "Lucene Index Upgrader"

val luceneVersion = "8.5.1"

plugins {
    `java-library`
    `maven-publish`
    signing
    `utf8-workarounds`
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.apache.lucene:lucene-core:$luceneVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
    testImplementation("org.apache.lucene:lucene-backward-codecs:$luceneVersion")
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            pom {
                licenses {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }

                scm {
                    url.set("https://github.com/trejkaz/luceneupgrader")
                    connection.set("git@github.com:trejkaz/luceneupgrader")
                    developerConnection.set("scm:git:git@github.com:trejkaz/luceneupgrader")
                }

                issueManagement {
                    system.set("GitHub Issues")
                    url.set("https://github.com/trejkaz/luceneupgrader/issues")
                }

                developers {
                    developer {
                        name.set("Hakanai")
                        email.set("hakanai@ephemeral.garden")
                        roles.add("Project Lead")
                    }
                }
            }
        }
    }

    repositories {
        val repoUrl = if (version.toString().contains("SNAPSHOT")) {
            "https://oss.sonatype.org/content/repositories/snapshots"
        } else {
            "https://oss.sonatype.org/service/local/staging/deploy/maven2"
        }
        maven(repoUrl) {
            credentials {
                username = System.getenv("DEPLOY_USER")
                password = System.getenv("DEPLOY_PASS")
            }
        }
    }
}
