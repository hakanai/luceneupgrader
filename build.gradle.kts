plugins {
    application
    `utf8-workarounds`
    id("com.vanniktech.maven.publish") version "0.34.0"
}

version = "0.8.1-SNAPSHOT"
group = "garden.ephemeral.luceneupgrader"
description = "Lucene Index Upgrader"

java {
    sourceCompatibility = JavaVersion.VERSION_11
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

mavenPublishing {
    pom {
        name.set(project.name)
        url.set("https://github.com/trejkaz/luceneupgrader")
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

    publishToMavenCentral()

    signAllPublications()
}
