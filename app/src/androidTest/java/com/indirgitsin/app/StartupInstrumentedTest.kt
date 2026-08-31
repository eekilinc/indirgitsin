package com.indirgitsin.app

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupInstrumentedTest {
    @Test fun mainActivityLaunchesAndResumes() {
        // Keep the OS permission sheet from taking focus during the Activity lifecycle assertion.
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                assertTrue(activity.window.decorView.isShown)
            }
        }
    }
}
