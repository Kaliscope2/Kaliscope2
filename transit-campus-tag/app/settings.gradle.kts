rootProject.name = "tagdemo-app"

// Pulls the pure-Kotlin core module (fare/access logic + APDU protocol) into
// this build as ":core", without needing it inside the same settings tree
// as the Android build — that keeps `gradle test` in core/ working in
// environments (like sandboxes) that don't have the Android SDK at all.
includeBuild("../core") {
    dependencySubstitution {
        substitute(module("nz.kaliscope.tagdemo:core")).using(project(":"))
    }
}
