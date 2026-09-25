plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // KEIN kotlin.android-Plugin: seit AGP 9.0 ist Kotlin eingebaut, und das
    // Plugin ist mit der neuen DSL nicht mehr kompatibel.
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
