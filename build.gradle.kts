// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Detekt plugin is applied in modules that need it. Version is declared here for consistency.
    id("io.gitlab.arturbosch.detekt") version "1.23.1" apply false
}

// Convenience CI task to run common checks
tasks.register("ciChecks") {
    group = "verification"
    description = "Run lint, tests and assembleDebug for CI validation"
    dependsOn(":app:lint", 
              ":app:test", 
              ":app:assembleDebug")
}