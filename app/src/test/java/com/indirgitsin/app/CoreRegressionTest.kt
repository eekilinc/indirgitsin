package com.indirgitsin.app

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CoreRegressionTest(private val name: String, private val check: () -> Unit) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = CoreRegressionChecks.cases.map { arrayOf(it.first, it.second) }
    }
    @Test fun regression() { check() }
}
