plugins {
    id("com.gradle.plugin-publish") version "0.10.0"
    id("java")
    id("java-gradle-plugin")
}

version = "1.0-SNAPSHOT"

dependencies {
    implementation(gradleApi())
    implementation("com.rabbitmq:amqp-client:5.2.0")
    implementation("commons-io:commons-io:2.6")
    implementation("com.github.jengelman.gradle.plugins:shadow:2.0.4")
    implementation("org.apache.commons:commons-lang3:3.5")
    implementation(project(":rewind-core"))
}

repositories {
    mavenCentral()
    jcenter()
}

gradlePlugin {
    (plugins) {
        create("rewindBase") {
            id = "org.rewindframework.rewind-base"
            implementationClass = "org.rewindframework.gradle.plugins.RewindBasePlugin"
        }
    }
}

pluginBundle {
    website = "https://rewindframework.org/"
    vcsUrl = "https://github.com/rewindframework/rewind"
    description = "Rewind base plugin"
    tags = listOf("rewind", "end-to-end-testing")

    plugins {
        getByName("rewindBase") {
            // id is captured from java-gradle-plugin configuration
            displayName = "Rewind base plugin"
        }
    }
}

tasks.create("continuousIntegration") {
    dependsOn(tasks.getByName("check"))
}
