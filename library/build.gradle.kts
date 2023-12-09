import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.lsplugin.jgit)
    alias(libs.plugins.lsplugin.publish)
    `maven-publish`
    signing
}

android {
    compileSdk = 34
    buildToolsVersion = "34.0.0"
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
        targetSdk = 34
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
