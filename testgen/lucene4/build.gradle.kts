repositories {
    mavenCentral()
}

val luceneVersions = listOf(
    "4.0.0",
    "4.1.0",
    "4.2.0", "4.2.1",
    "4.3.0", "4.3.1",
    "4.4.0",
    "4.5.0", "4.5.1",
    "4.6.0", "4.6.1",
    "4.7.0", "4.7.1", "4.7.2",
    "4.8.0", "4.8.1",
    "4.9.0", "4.9.1",
    "4.10.0", "4.10.1", "4.10.2", "4.10.3", "4.10.4"
).associateWith { version -> listOf(
    "org.apache.lucene:lucene-core:$version",
    "org.apache.lucene:lucene-analyzers-common:$version"
)}

val runAll by tasks.registering

luceneVersions.forEach { (version, artifacts) ->
    val configuration = configurations.register("runtimeLucene$version")
    dependencies {
        artifacts.forEach { a -> add("runtimeLucene$version", a) }
    }
    val runTask = tasks.register("runLucene$version", Exec::class) {
        executable = "jjs"

        // Workaround for a bug which will never be fixed:
        // https://bugs.openjdk.java.net/browse/JDK-8203853
        val classpath = configuration.get().asPath.replace(";C:", ";")

        args("-classpath", classpath, "test-gen.js", "--", version)
        doFirst {
            mkdir(buildDir)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
