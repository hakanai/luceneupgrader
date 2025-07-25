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
    "6.0.0", "6.0.1",
    "6.1.0",
    "6.2.0", "6.2.1",
    "6.3.0",
    "6.4.0", "6.4.1", "6.4.2",
    "6.5.0", "6.5.1",
    "6.6.0", "6.6.1", "6.6.2", "6.6.3", "6.6.4", "6.6.5", "6.6.6",
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
        // Some 6.x versions of Lucene access NIO classes; not an issue in lower or higher versions.
        jvmArgs("--add-opens", "java.base/java.nio=ALL-UNNAMED")
        doFirst {
            mkdir(buildDir)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
