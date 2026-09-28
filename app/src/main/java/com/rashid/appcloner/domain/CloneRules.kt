package com.rashid.appcloner.domain

/** A deliberately conservative result: this release does not rewrite or re-sign APKs. */
sealed interface Compatibility {
    data object NotAssessed : Compatibility
    data class Unsupported(val reason: String) : Compatibility
}

/** Extension point for a legally supported APK transformation backend. */
interface ApkCloneEngine {
    fun assessCompatibility(packageName: String): Compatibility
}

/** Explicitly unavailable backend: it never pretends to build or sign an APK. */
object UnsupportedApkCloneEngine : ApkCloneEngine {
    override fun assessCompatibility(packageName: String): Compatibility = Compatibility.Unsupported(
        "Independent APK generation for $packageName is not implemented. Android provides no " +
            "general public API for safely cloning arbitrary installed apps."
    )
}

object CloneCompatibilityChecker {
    fun assess(packageName: String): Compatibility =
        UnsupportedApkCloneEngine.assessCompatibility(packageName)
}

object CloneNameRules {
    fun validate(name: String): String? = when {
        name.isBlank() -> "Enter a name."
        name.trim().length > 40 -> "Use 40 characters or fewer."
        name.any { it.isISOControl() } -> "The name contains unsupported characters."
        else -> null
    }
}

object PackageIdRules {
    private val valid = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun validate(candidate: String, existingPackages: Set<String>): String? = when {
        !valid.matches(candidate) -> "Use a dotted identifier, such as com.example.clone."
        candidate in existingPackages -> "That package identifier is already installed."
        else -> null
    }
}
