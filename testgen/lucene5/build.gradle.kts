repositories {
    mavenCentral()
}

val luceneVersions = listOf(
    "5.0.0",
    "5.1.0",
    "5.2.0", "5.2.1",
    "5.3.0", "5.3.1", "5.3.2",
    "5.4.0", "5.4.1",
    "5.5.0", "5.5.1", "5.5.2", "5.5.3", "5.5.4", "5.5.5"
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
