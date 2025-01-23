import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.lsplugin.jgit)
    alias(libs.plugins.lsplugin.publish)
    `maven-publish`
    signing
}

android {
    compileSdk = 35
    buildToolsVersion = "35.0.1"
    namespace = "org.lsposed.hiddenapibypass.library"

    buildFeatures {
        androidResources = false
        buildConfig = true
    }
    defaultConfig {
        minSdk = 1
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        targetSdk = 35
    }
    buildTypes {
        release {
            consumerProguardFiles("consumer-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    packaging {
        resources {
            excludes += "/META-INF/**"
            excludes += "/*.properties"
        }
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    compileOnly(projects.stub)
    compileOnly(libs.androidx.annotation)
    androidTestImplementation(libs.test.ext.junit)
    androidTestImplementation(libs.test.rules)
    androidTestCompileOnly(projects.stub)
}

@CacheableTask
abstract class ManifestUpdater : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val outputManifest: RegularFileProperty

    @TaskAction
    fun taskAction() {
        outputManifest.get().asFile.writeText(
            mergedManifest.get().asFile.readText()
                .replace("<uses-sdk ", "<uses-sdk android:targetSdkVersion=\"35\" ")
        )
    }
}


androidComponents.onVariants { variant ->
    val variantName = variant.name
    val manifestUpdater =
        project.tasks.register("${variantName}ManifestUpdater", ManifestUpdater::class.java)
    variant.artifacts.use(manifestUpdater)
        .wiredWithFiles(
            ManifestUpdater::mergedManifest,
            ManifestUpdater::outputManifest
        )
        .toTransform(SingleArtifact.MERGED_MANIFEST)
}


val repo = jgit.repo(true)
version = repo?.latestTag?.removePrefix("v") ?: "0.0"
println("${rootProject.name} version: $version")

publish {
    githubRepo = "LSPosed/AndroidHiddenApiBypass"
    publications {
        register<MavenPublication>("hiddenapibypass") {
            group = "org.lsposed.hiddenapibypass"
            artifactId = "hiddenapibypass"
            version = version
            afterEvaluate {
                from(components.getByName("release"))
            }
            pom {
                name = "Android Hidden Api Bypass"
                description = "Bypass restrictions on non-SDK interfaces"
                url = "https://github.com/LSPosed/AndroidHiddenApiBypass"
                licenses {
                    license {
                        name = "The Apache Software License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        name = "LSPosed"
                        url = "https://lsposed.org"
                    }
                }
                scm {
                    connection = "scm:git:https://github.com/LSPosed/AndroidHiddenApiBypass.git"
                    url = "https://github.com/LSPosed/AndroidHiddenApiBypass"
                }
            }
        }
    }
}
