plugins {
    `java-library`
}

dependencies {
    api("commons-io:commons-io:2.6")
}

repositories {
    mavenCentral()
}

tasks.create("continuousIntegration") {
    dependsOn(tasks.getByName("check"))
}
