plugins {
    application
}

description = "Command-line front end: analyse a Java file and print or save the report."

dependencies {
    implementation(project(":core"))
}

application {
    mainClass.set("com.aliibrahim.nullcheck.cli.Main")
    applicationName = "null-check"
}

tasks.named<JavaExec>("run") {
    // Resolve relative paths in --args against the repository root.
    workingDir = rootProject.projectDir
}
