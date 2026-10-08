// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  // Declared here so the Kotlin JVM and Compose Multiplatform plugins share one classpath version
  // with the Android plugin; the desktop module applies them.
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.compose.multiplatform) apply false
}
