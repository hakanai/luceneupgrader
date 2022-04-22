repositories {
    mavenCentral()
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
    val configuration = configurations.register("runtimeLucene$version")
    dependencies {
        add("runtimeLucene$version", artifact)
    }
    val runTask = tasks.register("runLucene$version", Exec::class) {
        executable = "jjs"
        args("-classpath", configuration.get().asPath, "test-gen.js", "--", version)
        doFirst {
            mkdir(buildDir)
        }
    }
    runAll.configure {
        dependsOn(runTask)
    }
}
