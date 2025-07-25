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
    "3.0.0", "3.0.1", "3.0.2", "3.0.3",
    "3.1.0",
    "3.2.0",
    "3.3.0",
    "3.4.0",
    "3.5.0",
    "3.6.0", "3.6.1", "3.6.2"
).associateWith { v -> "org.apache.lucene:lucene-core:$v" }

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
