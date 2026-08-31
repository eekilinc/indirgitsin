package com.indirgitsin.app

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun linkEntryEnablesResolveWithoutStartingNetworkWork() {
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("link_input").assertIsDisplayed()
            compose.onNodeWithTag("resolve_button").assertIsNotEnabled()
            compose.onNodeWithTag("link_input").performTextInput("https://youtu.be/dQw4w9WgXcQ")
            compose.onNodeWithTag("resolve_button").assertIsEnabled()
            compose.onNodeWithTag("link_input").performTextClearance()
            compose.onNodeWithTag("resolve_button").assertIsNotEnabled()
        }
    }
}
