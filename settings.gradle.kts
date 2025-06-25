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
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
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
