plugins {
    alias(libs.plugins.javafx) apply false
}

allprojects {
    group = "com.aliibrahim.nullcheck"
    version = "2.0.0"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
    }

    val libs = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Compile for Java 21 with whichever JDK (21 or newer) runs Gradle; no exact toolchain needed.
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
    }

    tasks.withType<Javadoc>().configureEach {
        // Fail on missing or broken docs for the public API.
        (options as StandardJavadocDocletOptions).apply {
            encoding = "UTF-8"
            addBooleanOption("Xdoclint:all", true)
            addBooleanOption("Werror", true)
            quiet()
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Tests read the sample inputs from the shared samples/ folder.
        systemProperty("samples.dir", rootProject.file("samples").absolutePath)
    }
}
