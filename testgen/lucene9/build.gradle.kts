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
    "9.0.0",
    "9.1.0",
    "9.2.0",
    "9.3.0",
    "9.4.0", "9.4.1", "9.4.2",
    "9.5.0",
    "9.6.0",
    "9.7.0",
    "9.8.0",
    "9.9.0", "9.9.1", "9.9.2",
    "9.10.0",
    "9.11.0", "9.11.1"
).associateWith { version -> listOf(
    "org.apache.lucene:lucene-core:$version",
    "org.apache.lucene:lucene-analysis-common:$version"
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