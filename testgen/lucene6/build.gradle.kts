repositories {
    mavenCentral()
}

val luceneVersions = listOf(
    "6.0.0", "6.0.1",
    "6.1.0",
    "6.2.0", "6.2.1",
    "6.3.0",
    "6.4.0", "6.4.1", "6.4.2",
    "6.5.0", "6.5.1",
    "6.6.0", "6.6.1", "6.6.2", "6.6.3", "6.6.4", "6.6.5",
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
