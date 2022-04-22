repositories {
    mavenCentral()
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
