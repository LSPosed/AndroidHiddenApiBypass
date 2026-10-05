plugins {
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.lsplugin.publish)
}
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(21)
    vendor = JvmVendorSpec.ADOPTIUM
}
