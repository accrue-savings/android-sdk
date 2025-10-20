import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("maven-publish")
}



android {
    group = "com.accruesavings.androidsdk"
    version = "v1.3.2"
    namespace = "com.accruesavings.androidsdk"
    compileSdk = 34

    buildFeatures {
        compose = true
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }

    lint {
        disable += "CoroutineCreationDuringComposition"
    }
}



dependencies {
    implementation(libs.androidx.browser)
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // Google Pay API for Push Provisioning
    implementation("com.google.android.gms:play-services-pay:16.1.0")
    implementation("com.google.android.gms:play-services-wallet:19.2.1")
    // TapAndPay API for push provisioning functionality - using Maven Central
    implementation("com.google.android.gms:play-services-tapandpay:18.3.3")
    // Google Sign-In for wallet account ID
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("androidx.browser:browser:1.5.0")
    implementation("androidx.webkit:webkit:1.14.0")
}


val githubProperties = Properties()
githubProperties.load(rootProject.file("github.properties").inputStream())

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.accruesavings"
            artifactId = "androidsdk"
            version = "v1.3.2"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/accrue-savings/android-sdk")
            credentials {
                username = githubProperties["gpr.usr"] as String? ?: System.getenv("GPR_USER")
                password = githubProperties["gpr.key"] as String? ?: System.getenv("GPR_API_KEY")
            }
        }
    }
}
//
//afterEvaluate {
//    publishing {
//        publications {
//            register<MavenPublication>("release") {
//                // from(components["release"])
//
//                groupId = "com.github.accrue-savings"
//                artifactId = "android-sdk"
//                version = "v1.3.1"
//
//                afterEvaluate {
//                    from(components["release"])
//                }
//            }
//        }
//    }
//}
