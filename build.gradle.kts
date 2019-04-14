plugins {
    java
    id("net.researchgate.release") version "2.8.0"
    id("com.techshroom.incise-blue") version "0.3.10"
    id("com.github.johnrengelman.shadow") version "4.0.4"
    id("org.openjfx.javafxplugin") version "0.0.7"
    idea
}

configurations.all {
    resolutionStrategy {
        failOnVersionConflict()

        force(
                "com.google.guava:guava:27.0.1-jre",
                "com.google.code.findbugs:jsr305:3.0.2"
        )
    }
}

inciseBlue {
    ide()
    license()
    util {
        setJavaVersion("11")
    }
    lwjgl {
        lwjglVersion = "3.1.3"
        addDependency("tinyfd")
    }
}

dependencies {
    implementation(group = "com.techshroom", name = "UnplannedDescent.api", version = project.property("ud.version").toString())
    implementation(group = "com.techshroom", name = "UnplannedDescent.implementation", version = project.property("ud.version").toString())

    implementation(group = "org.slf4j", name = "slf4j-api", version = "1.7.25")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.2.3")

    implementation(group = "com.techshroom", name = "jsr305-plus", version = "0.0.1")

    val guice = mutableMapOf("group" to "com.google.inject", "name" to "guice", "version" to "4.1.0")
    if (project.findProperty("debugging").toString().toBoolean()) {
        println("Using no_aop guice for debugging!")
        guice["classifier"] = "no_aop"
    }
    implementation(guice)

    implementation(group = "com.google.guava", name = "guava", version = "27.0.1-jre")

    implementation(group = "com.squareup", name = "javapoet", version = "1.9.0")

    implementation(group = "com.squareup.okio", name = "okio", version = "1.13.0")

    implementation(group = "com.flowpowered", name = "flow-math", version = "1.0.3")

    annotationProcessor(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc3")
    annotationProcessor(group = "com.google.auto.value", name = "auto-value", version = "1.6.1")

    testImplementation(group = "junit", name = "junit", version = "4.12")
}

javafx {
    version = "12"
    modules("javafx.base", "javafx.graphics", "javafx.controls", "javafx.fxml")
}

tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "com.techshroom.midishapes.MidiShapes")
    }
}
