import java.util.Properties

val githubProperties = Properties()
githubProperties.load(file("github-read.properties").inputStream())

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // Local repository for tap-and-pay SDK
        flatDir {
            dirs("tap-and-pay")
        }
        maven {
            url = uri("file://${settingsDir}/tap-and-pay")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Local repository for tap-and-pay SDK
        flatDir {
            dirs("tap-and-pay")
        }
        maven {
            url = uri("file://${settingsDir}/tap-and-pay")
        }
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/accrue-savings/android-sdk")
            credentials {
                username = githubProperties["gpr.usr"] as String? ?: System.getenv("GITHUB_USERNAME")
                password = githubProperties["gpr.key"] as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "Accrue Android SDK Demo"
include(":androidsdk", ":app")
