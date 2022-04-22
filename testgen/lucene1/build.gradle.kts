repositories {
    mavenCentral()
}

val luceneVersions = listOf(
    "1.2",
    "1.3",
    "1.4.1", "1.4.2", "1.4.3"
).associateWith { v -> "lucene:lucene:$v" } +
        mapOf("1.9.1" to "org.apache.lucene:lucene-core:1.9.1")

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
