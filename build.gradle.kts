
version = "0.6.0"
group = "garden.ephemeral.luceneupgrader"
description = "Lucene Index Upgrader"

plugins {
    application
    `maven-publish`
    signing
    `utf8-workarounds`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest-library:2.2")
}

application {
    mainClass.set("org.trypticon.luceneupgrader.cli.Main")
}

tasks.javadoc {
    exclude("**/internal/**/*.java")
}

tasks.jar {
    manifest {
        // Gradle's application plugin doesn't add this for us :(
        attributes["Main-Class"] = application.mainClass.get()
    }
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])

            pom {
                description.set(project.description)

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
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
            "https://s01.oss.sonatype.org/content/repositories/snapshots/"
        } else {
            "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
        }
        maven(repoUrl) {
            val user = System.getenv("DEPLOY_USER")
            val pass = System.getenv("DEPLOY_PASS")
            if (user != null && pass != null) {
                credentials {
                    username = user
                    password = pass
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["mavenJava"])
}
