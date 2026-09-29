import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
}

// Balance simulator: plays many AI-vs-AI games with generated decks and reports
// win rates per race, King, race combination and card. Not shipped in the app.
//   ./gradlew :core:balanceReport -Pgames=4000
val balance: SourceSet by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

tasks.register<JavaExec>("balanceReport") {
    group = "verification"
    description = "Simulates AI-vs-AI games and writes docs/BALANCE.md"
    classpath = balance.runtimeClasspath
    mainClass.set("com.kingofthebeasts.core.balance.BalanceReportKt")
    workingDir = rootProject.projectDir
    args(
        (project.findProperty("games") as String?) ?: "2000",
        (project.findProperty("ai") as String?) ?: "greedy",
        (project.findProperty("out") as String?) ?: "docs/BALANCE.md",
        *listOfNotNull(project.findProperty("king") as String?).toTypedArray(),
    )
}

// Measures how much +1 HP is worth to each race and solves for the HP changes that
// equalise race strength (see BalanceSolver.kt).  ./gradlew :core:balanceSolve -Pgames=2000
tasks.register<JavaExec>("balanceSolve") {
    group = "verification"
    description = "Solves for per-race HP adjustments that equalise race strength"
    classpath = balance.runtimeClasspath
    mainClass.set("com.kingofthebeasts.core.balance.BalanceSolverKt")
    workingDir = rootProject.projectDir
    args(
        (project.findProperty("games") as String?) ?: "2000",
        (project.findProperty("ai") as String?) ?: "greedy",
        (project.findProperty("out") as String?) ?: "docs/BALANCE_SOLVE.md",
        (project.findProperty("step") as String?) ?: "3",
    )
}
