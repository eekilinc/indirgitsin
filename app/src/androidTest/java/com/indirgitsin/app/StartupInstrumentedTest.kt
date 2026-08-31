package com.indirgitsin.app

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupInstrumentedTest {
    @Test fun rhinoRuntimeSurvivesReleaseShrinking() {
        // Reflective entry also checks that R8 preserves the JavaScript runtime's public names.
        val contextClass = Class.forName("org.mozilla.javascript.Context")
        val scriptableClass = Class.forName("org.mozilla.javascript.Scriptable")
        val runtime = contextClass.getMethod("enter").invoke(null)
        try {
            contextClass.getMethod("setOptimizationLevel", Int::class.javaPrimitiveType).invoke(runtime, -1)
            val scope = contextClass.getMethod("initSafeStandardObjects").invoke(runtime)
            val evaluate = contextClass.getMethod("evaluateString", scriptableClass, String::class.java,
                String::class.java, Int::class.javaPrimitiveType, Any::class.java)
            assertEquals("fedcba", evaluate.invoke(runtime, scope,
                "'abcdef'.split('').reverse().join('')", "runtime-test", 1, null).toString())
            assertEquals("{\"sound\":true,\"samples\":[0,1024]}", evaluate.invoke(runtime, scope,
                "JSON.stringify({sound:true,samples:[0,1024]})", "runtime-test", 1, null).toString())
        } finally { contextClass.getMethod("exit").invoke(null) }
    }

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
