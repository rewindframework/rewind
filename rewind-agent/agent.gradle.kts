plugins {
    application
}

version = "1.0-SNAPSHOT"

application {
    mainClassName = "org.rewindframework.agent.AgentMain"
}

tasks.getByName<JavaExec>("run").args("--host", "localhost", "--queue", "hello", "--worker-dir", file("build/some-worker-dir"))

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-api:2.11.0")
    implementation("org.apache.logging.log4j:log4j-core:2.11.0")
//    implementation group: 'org.apache.logging.log4j', name: 'log4j-jcl', version: '2.11.0'
    implementation("com.rabbitmq:amqp-client:5.2.0")
    implementation("commons-io:commons-io:2.6")
    implementation("commons-cli:commons-cli:1.4")
    implementation("org.apache.commons:commons-lang3:3.5")
    implementation(project(":rewind-core"))
}

repositories {
    mavenCentral()
}

tasks.getByName<Jar>("jar").manifest.attributes(mapOf("Main-Class" to application.mainClassName))

tasks.create("continuousIntegration") {
    dependsOn(tasks.getByName("check"))
}
