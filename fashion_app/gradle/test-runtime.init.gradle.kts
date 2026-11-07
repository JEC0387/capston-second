// Keep Robolectric's lock/framework cache in the writable Gradle cache, including in cloud workspaces.
val testUserHome = gradle.gradleUserHomeDir.resolve("caches/robolectric-home").apply { mkdirs() }
allprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        // Failure evidence must remain readable even if the report-artifact quota is exhausted.
        testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        systemProperty("user.home", testUserHome.absolutePath)
        systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
        // Preserve the launcher JVM's configured proxy and trusted CA without copying credentials.
        listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore").forEach { name ->
            System.getProperty(name)?.let { value -> systemProperty(name, value) }
        }
    }
}
