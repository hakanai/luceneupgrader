plugins {
    `java-library`
}

dependencies {
    val graalVersion = "22.0.0.2"
    implementation("org.graalvm.js:js:$graalVersion")
    implementation("org.graalvm.js:js-scriptengine:$graalVersion")
}
