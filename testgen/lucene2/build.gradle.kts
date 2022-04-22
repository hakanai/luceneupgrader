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
    "2.0.0",
    "2.1.0",
    "2.2.0",
    "2.3.0", "2.3.1", "2.3.2",
    "2.4.0", "2.4.1",
    "2.9.0", "2.9.1",
    "2.9.2", "2.9.3", "2.9.4"
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
            mkdir(buildDir)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
