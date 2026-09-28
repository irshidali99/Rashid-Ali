package com.rashid.appcloner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloneRulesTest {
    @Test fun cloneNameRejectsBlankAndLongNames() {
        assertEquals("Enter a name.", CloneNameRules.validate("  "))
        assertEquals("Use 40 characters or fewer.", CloneNameRules.validate("x".repeat(41)))
        assertNull(CloneNameRules.validate("Work Copy"))
    }

    @Test fun packageIdMustBeDottedAndNotCollide() {
        assertNull(PackageIdRules.validate("com.example.copy", setOf("com.other.app")))
        assertEquals("That package identifier is already installed.", PackageIdRules.validate("com.example.copy", setOf("com.example.copy")))
        assertEquals("Use a dotted identifier, such as com.example.clone.", PackageIdRules.validate("not-valid", emptySet()))
    }

    @Test fun arbitraryInstalledPackagesAreExplicitlyUnsupported() {
        val result = CloneCompatibilityChecker.assess("org.example.app")
        assertTrue(result is Compatibility.Unsupported)
    }
}
