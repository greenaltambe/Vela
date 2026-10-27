// Shared by every JVM extractor module: a `liveTest` source set and task for tests that hit the real network or
// real binaries. It is kept out of `test` and `check`, so ordinary builds never depend on either.
val sourceSets = extensions.getByType<SourceSetContainer>()
val main = sourceSets.getByName("main")
val test = sourceSets.getByName("test")

val liveTest = sourceSets.create("liveTest") {
    compileClasspath += main.output + test.output
    runtimeClasspath += main.output + test.output
}
configurations.getByName(liveTest.implementationConfigurationName).extendsFrom(configurations.getByName("testImplementation"))
configurations.getByName(liveTest.runtimeOnlyConfigurationName).extendsFrom(configurations.getByName("testRuntimeOnly"))

tasks.register<Test>("liveTest") {
    group = "verification"
    description = "Runs tests against the real YouTube / real yt-dlp. Not part of check."
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.runtimeClasspath
    useJUnit()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
