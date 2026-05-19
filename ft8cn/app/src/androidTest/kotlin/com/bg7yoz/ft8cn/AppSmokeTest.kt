package com.bg7yoz.ft8cn

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import radio.ks3ckc.ft8us.ComposeMainActivity

/**
 * Instrumented smoke test. Launches the real ComposeMainActivity on a device
 * (or emulator) and asserts it reaches RESUMED without throwing.
 *
 * Not run in CI yet — the GitHub Actions workflow has no emulator wired up.
 * Local invocation:
 *   cd ft8cn && cmd.exe /c "gradlew.bat connectedDebugAndroidTest"
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun composeMainActivity_launchesAndReachesResumed() {
        ActivityScenario.launch(ComposeMainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertThat(activity).isNotNull()
                assertThat(activity.isFinishing).isFalse()
            }
        }
    }
}
