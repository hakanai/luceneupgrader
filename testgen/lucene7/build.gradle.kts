repositories {
    mavenCentral()
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
