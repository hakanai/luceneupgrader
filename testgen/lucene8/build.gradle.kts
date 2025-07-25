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
    "8.0.0",
    "8.1.0", "8.1.1",
    "8.2.0",
    "8.3.0", "8.3.1",
    "8.4.0", "8.4.1",
    "8.5.0", "8.5.1", "8.5.2",
    "8.6.0", "8.6.1", "8.6.2", "8.6.3",
    "8.7.0",
    "8.8.0", "8.8.1", "8.8.2",
    "8.9.0",
    "8.10.0", "8.10.1",
    "8.11.0", "8.11.1", "8.11.2", "8.11.3", "8.11.4",
).associateWith { version -> listOf(
    "org.apache.lucene:lucene-core:$version",
    "org.apache.lucene:lucene-analyzers-common:$version"
)}

val runAll by tasks.registering

luceneVersions.forEach { (version, artifacts) ->
    val configuration = configurations.register("runtimeLucene$version") {
        extendsFrom(configurations.runtimeClasspath.get())
    }

    dependencies {
        artifacts.forEach { a -> add("runtimeLucene$version", a) }
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
