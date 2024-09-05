
plugins {
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    repositories {
        mavenCentral()

        maven("https://papermc.io/repo/repository/maven-public/")
        maven("https://ci.frostcast.net/plugin/repository/everything")
        maven("https://maven.enginehub.org/repo/")
        maven("https://jitpack.io")
    }
}
