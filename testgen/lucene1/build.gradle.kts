plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":testgen:common"))
}

val luceneVersions = listOf(
    "1.2",
    "1.3",
    "1.4.1", "1.4.2", "1.4.3"
).associateWith { v -> "lucene:lucene:$v" } +
        mapOf("1.9.1" to "org.apache.lucene:lucene-core:1.9.1")

val runAll by tasks.registering

luceneVersions.forEach { (version, artifact) ->
    val configuration = configurations.register("runtimeLucene$version") {
        extendsFrom(configurations.runtimeClasspath.get())
    }

    dependencies {
        add("runtimeLucene$version", artifact)
    }

    val runTask = tasks.register("runLucene$version", JavaExec::class) {
        classpath = configuration.get()
        mainClass.set("RunScript")
        args("test-gen.js", version)
        doFirst {
            mkdir(layout.buildDirectory)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
