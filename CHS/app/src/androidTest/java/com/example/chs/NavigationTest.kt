package com.example.chs

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chs.ui.theme.CHSTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = "navigation_test.db"
    private val prefs = "navigation_test_profiles"

    @Before
    fun start() {
        context.deleteDatabase(db)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
        compose.setContent { CHSTheme { PulseApp(db, prefs) } }
    }

    @After
    fun cleanup() {
        context.deleteDatabase(db)
        context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun homeShowsBothMenus() {
        compose.onNodeWithText("CHS").assertExists()
        compose.onNodeWithText("Puls").assertExists()
        compose.onNodeWithText("Oxigen (SpO₂)").assertExists()
        compose.onAllNodesWithText("Nicio măsurătoare").assertCountEquals(2)
    }

    @Test
    fun opensEachMenuAndReturnsHome() {
        compose.onNodeWithText("Oxigen (SpO₂)").performClick()
        compose.onNodeWithText("Saturație medie: -- %").assertExists()
        compose.onNodeWithText("‹ Înapoi").performClick()
        compose.onNodeWithText("CHS").assertExists()

        compose.onNodeWithText("Puls").performClick()
        compose.onNodeWithText("Puls mediu: -- bpm").assertExists()
        compose.onNodeWithText("‹ Înapoi").performClick()
        compose.onNodeWithText("CHS").assertExists()
    }

    @Test
    fun sampleProfileShowsLatestValuesAndOxygenHistory() {
        compose.onNodeWithText("Profil: Profil personal ▾").performClick()
        compose.onNodeWithText("Andrei Ionescu").performClick()
        compose.onAllNodesWithText("Ultima:", substring = true).assertCountEquals(2)

        compose.onNodeWithText("Oxigen (SpO₂)").performClick()
        compose.onNodeWithText("Saturație medie: 9", substring = true).assertExists()
        listOf("Săptămâni", "Luni", "Zile").forEach {
            compose.onNodeWithText(it).performClick()
            compose.onNodeWithText("Saturație medie: 9", substring = true).assertExists()
        }
        compose.onNodeWithText("‹").performClick()
        compose.onNodeWithText("Saturație medie: 9", substring = true).assertExists()
    }

    @Test
    fun profileChoiceIsSharedBetweenMenus() {
        compose.onNodeWithText("Profil: Profil personal ▾").performClick()
        compose.onNodeWithText("Maria Stan").performClick()
        compose.onNodeWithText("Puls").performClick()
        compose.onNodeWithText("Profil: Maria Stan ▾").assertExists()
        compose.onNodeWithText("‹ Înapoi").performClick()
        compose.onNodeWithText("Oxigen (SpO₂)").performClick()
        compose.onNodeWithText("Profil: Maria Stan ▾").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class OxygenMeasurementScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsInstructionsAndSensorDiagnostics() {
        compose.setContent { CHSTheme { OxygenMeasurementScreen(onBack = {}, onSave = {}) } }
        compose.onNodeWithText("Măsurare oxigen (SpO₂)").assertExists()
        compose.onNodeWithText("Acoperiți complet senzorul", substring = true).assertExists()
        compose.onNodeWithText("Senzori detectați", substring = true).performClick()
        // Every phone and emulator has at least an accelerometer, listed as "name — type (id)".
        assertTrue(compose.onAllNodesWithText(" — ", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun backButtonCallsBack() {
        var back = false
        compose.setContent { CHSTheme { OxygenMeasurementScreen(onBack = { back = true }, onSave = {}) } }
        compose.onNodeWithText("‹ Înapoi").performClick()
        compose.runOnIdle { assertTrue(back) }
    }
}
