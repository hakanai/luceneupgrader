
// Overrides a bunch of tasks to default to UTF-8, because for whatever reason,
// that is _still_ not the default encoding for all these tasks. :(

tasks.withType<AntlrTask> {
    arguments = arguments + listOf("-encoding", "UTF-8")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>{
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    defaultCharacterEncoding = "UTF-8"
}

tasks.withType<JavaExec> {
    defaultCharacterEncoding = "UTF-8"
}
