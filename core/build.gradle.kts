plugins {
    `java-library`
}

description = "UI-independent null-check analysis and report writers."

dependencies {
    implementation(libs.itextpdf)
    implementation(libs.tree.sitter)
    implementation(libs.tree.sitter.java)
    implementation(libs.tree.sitter.php)
    implementation(libs.tree.sitter.cpp)
}
