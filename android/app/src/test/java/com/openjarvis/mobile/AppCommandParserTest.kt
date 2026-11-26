package com.openjarvis.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCommandParserTest {
    @Test
    fun recognisesExplicitOpenCommandsAndAppAliases() {
        assertEquals("GPay", extractOpenAppTarget("Hey Jarvis, open GPay"))
        assertEquals("WhatsApp", extractOpenAppTarget("please open WhatsApp app"))
        assertEquals("Calculator", extractOpenAppTarget("launch the Calculator"))
        assertEquals("GPay", extractOpenAppTarget("GPay kholo"))
        assertEquals("Google Pay", extractOpenAppTarget("Google Pay open karo"))
        assertEquals("GPay", extractOpenAppTarget("Hey Jarvis, GPay kholo"))
        assertEquals("WhatsApp", extractOpenAppTarget("Jarvis WhatsApp kholo"))
        assertEquals("Instagram", extractOpenAppTarget("Instagram kholna"))
        assertEquals("GPay", extractOpenAppTarget("GPay ko khol do"))
        assertEquals("GPay", extractOpenAppTarget("open GPay please"))
    }

    @Test
    fun doesNotTreatOrdinaryQuestionsAsAppCommands() {
        assertNull(extractOpenAppTarget("Can you explain how to open GPay?"))
        assertNull(extractOpenAppTarget("open"))
        assertNull(extractOpenAppTarget("I want to start learning Kotlin"))
    }

    @Test
    fun appNamesMatchCommonAliasesWithoutMatchingUnrelatedApps() {
        assertTrue(appLabelMatchesTarget("Google Pay", "com.google.android.apps.nbu.paisa.user", "GPay"))
        assertTrue(appLabelMatchesTarget("WhatsApp", "com.whatsapp", "WhatsApp"))
        assertFalse(appLabelMatchesTarget("Google Maps", "com.google.android.apps.maps", "GPay"))
    }
}
