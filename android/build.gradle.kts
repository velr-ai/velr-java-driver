plugins {
    id("com.android.library") version "8.7.3"
    `maven-publish`
    signing
}

group = "ai.velr"
version = providers.gradleProperty("velrVersion")
    .orElse(providers.environmentVariable("VELR_VERSION"))
    .orElse("0.1.0-SNAPSHOT")
    .get()

val centralBundleDir = providers.gradleProperty("centralBundleDir")
    .orElse(providers.environmentVariable("CENTRAL_BUNDLE_DIR"))
    .orElse(layout.buildDirectory.dir("central-bundle-repository").map { it.asFile.absolutePath })

fun org.gradle.api.publish.maven.MavenPom.velrPom(
    displayName: String,
    displayDescription: String,
) {
    name.set(displayName)
    description.set(displayDescription)
    url.set("https://velr.ai")
    licenses {
        license {
            name.set("MIT License")
            url.set("https://github.com/velr-ai/velr-java-driver/blob/main/LICENSE")
            distribution.set("repo")
        }
        license {
            name.set("Velr Runtime Binary Redistribution License")
            url.set("https://github.com/velr-ai/velr-java-driver/blob/main/LICENSE.runtime")
            distribution.set("repo")
        }
    }
    developers {
        developer {
            id.set("velr")
            name.set("Velr.ai")
            organization.set("Velr Tech AB")
            organizationUrl.set("https://velr.ai")
        }
    }
    scm {
        connection.set("scm:git:https://github.com/velr-ai/velr-java-driver.git")
        developerConnection.set("scm:git:ssh://git@github.com/velr-ai/velr-java-driver.git")
        url.set("https://github.com/velr-ai/velr-java-driver")
        tag.set("v${project.version}")
    }
}

val signingKey = providers.gradleProperty("signingInMemoryKey")
    .orElse(providers.environmentVariable("SIGNING_KEY"))
val signingPassword = providers.gradleProperty("signingInMemoryKeyPassword")
    .orElse(providers.environmentVariable("SIGNING_PASSWORD"))
val signingTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("publish", ignoreCase = true) || it.contains("sign", ignoreCase = true)
}

val androidJavadocDir = layout.buildDirectory.dir("docs/androidJavadoc")
val releaseJavaClasses =
    layout.buildDirectory.dir("intermediates/javac/release/compileReleaseJavaWithJavac/classes")

val androidJavadoc by tasks.registering(Javadoc::class) {
    description = "Generates Javadoc for the Android API."
    dependsOn("compileReleaseJavaWithJavac")
    source(android.sourceSets["main"].java.srcDirs)
    classpath =
        files(android.bootClasspath) +
            configurations.getByName("releaseCompileClasspath") +
            files(releaseJavaClasses)
    destinationDir = androidJavadocDir.get().asFile
    exclude("ai/velr/internal/**")
    options.encoding = "UTF-8"
    (options as org.gradle.external.javadoc.StandardJavadocDocletOptions)
        .addStringOption("Xdoclint:all", "-quiet")
}

val androidJavadocJar by tasks.registering(Jar::class) {
    description = "A documentation JAR containing Javadoc for the Android API."
    dependsOn(androidJavadoc)
    archiveClassifier.set("javadoc")
    from(androidJavadocDir)
}

android {
    namespace = "ai.velr"
    compileSdk = 35

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

tasks.named("assemble") {
    dependsOn(androidJavadocJar)
}

afterEvaluate {
    publishing {
        repositories {
            maven {
                name = "CentralBundle"
                url = uri(centralBundleDir.get())
            }
        }
        publications {
            create<MavenPublication>("mavenAndroid") {
                from(components["release"])
                artifactId = "velr-java-driver-android"
                artifact(androidJavadocJar)
                pom {
                    velrPom(
                        "Velr Java Driver for Android",
                        "Android bindings for the embedded Velr graph database",
                    )
                }
            }
        }
    }
    signing {
        isRequired = !version.toString().endsWith("-SNAPSHOT") && signingTaskRequested
        if (signingKey.isPresent) {
            useInMemoryPgpKeys(signingKey.get(), signingPassword.orNull)
        }
        sign(publishing.publications)
    }
}
