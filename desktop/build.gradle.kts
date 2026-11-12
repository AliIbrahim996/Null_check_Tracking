plugins {
    application
    alias(libs.plugins.javafx)
}

description = "JavaFX desktop client for Null Check Tracking."

javafx {
    version = libs.versions.javafx.get()
    modules("javafx.controls")
}

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("com.aliibrahim.nullcheck.desktop.Launcher")
    applicationName = "null-check-tracking"
}

distributions {
    main {
        // Ship the sample inputs with the app.
        contents {
            from(rootProject.file("samples")) { into("samples") }
        }
    }
}

tasks.named<JavaExec>("run") {
    // Start in the samples folder so the open dialog lands next to the sample inputs.
    workingDir = rootProject.file("samples")
}
