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
    "7.0.0", "7.0.1",
    "7.1.0",
    "7.2.0", "7.2.1",
    "7.3.0", "7.3.1",
    "7.4.0",
    "7.5.0",
    "7.6.0",
    "7.7.0", "7.7.1", "7.7.2", "7.7.3",
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
            mkdir(layout.buildDirectory)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
