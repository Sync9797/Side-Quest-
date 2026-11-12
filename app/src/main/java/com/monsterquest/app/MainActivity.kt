package com.monsterquest.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.monsterquest.app.ui.AppRoot
import com.monsterquest.app.ui.FoldInfo
import com.monsterquest.app.ui.LocalFold
import com.monsterquest.app.ui.MonsterQuestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.monsterquest.core.StepSource
import com.monsterquest.core.FrameRate
import com.monsterquest.app.focus.FocusService

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var fold by mutableStateOf<FoldInfo?>(null)

    private var stepListener: SensorEventListener? = null

    override fun onResume() {
        super.onResume()
        vm.onAppResumed()
        startStepCounting()
        vm.refreshHealthStatus()
        vm.refreshHealthSteps()
    }

    override fun onPause() {
        stopStepCounting()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (vm.state.focus != null) FocusService.appReturned(this)
    }

    /**
     * Leaving for another app during a phone-free session fails it (after a
     * short grace). Turning the screen off is fine: the timer keeps going.
     */
    override fun onStop() {
        val screenOn = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        if (vm.state.focus != null && screenOn && !isChangingConfigurations) FocusService.appLeft(this)
        super.onStop()
    }

    /**
     * The step counter keeps counting in hardware while the app is closed, so
     * reading it whenever the app is open captures all walking since last time.
     */
    private fun startStepCounting() {
        if (vm.state.settings.stepSource != StepSource.PHONE) {
            stopStepCounting()
            return
        }
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val permitted = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        vm.stepSensorAvailable = sensor != null && permitted
        if (sensor == null || !permitted || stepListener != null) return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                vm.onStepCount(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        stepListener = listener
    }

    private fun stopStepCounting() {
        stepListener?.let { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(it) }
        stepListener = null
    }

    /** Picks the display mode closest to (not above) the requested refresh rate, at the current resolution. */
    private fun applyFrameRate(rate: FrameRate) {
        @Suppress("DEPRECATION")
        val screen = (if (Build.VERSION.SDK_INT >= 30) display else null) ?: windowManager.defaultDisplay
        val attrs = window.attributes
        val target = rate.hz
        if (target == null) {
            attrs.preferredDisplayModeId = 0
            attrs.preferredRefreshRate = 0f
        } else {
            val current = screen.mode
            val best = screen.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .filter { it.refreshRate <= target + 1f }
                .maxByOrNull { it.refreshRate } ?: current
            attrs.preferredDisplayModeId = best.modeId
            attrs.preferredRefreshRate = best.refreshRate
        }
        window.attributes = attrs
    }

    /** A widget can ask for a screen: eggs, adventures or quests. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { vm.openTarget = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash (the crest) while the app starts, then the game's loading screen.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val coldStart = savedInstanceState == null
        // Opened from a widget that asked for a screen.
        if (coldStart) intent?.getStringExtra(EXTRA_OPEN)?.let { vm.openTarget = it }

        // Health Connect has no live updates, so poll it while the game is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(60_000)
                    vm.refreshHealthSteps()
                    vm.regenerate()
                    startStepCounting()
                }
            }
        }

        // Track the hinge on foldables so layouts can avoid it and use
        // tabletop / book postures.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let {
                            FoldInfo(
                                isSeparating = it.isSeparating,
                                isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                                bounds = it.bounds,
                            )
                        }
                    }
            }
        }

        enableEdgeToEdge()
        setContent {
            // Ask for the chosen refresh rate; re-applied on fold/unfold (each screen has its own modes).
            val rate = vm.state.settings.frameRate
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.runtime.LaunchedEffect(rate, vm.batterySaver, config.screenWidthDp, config.screenHeightDp) {
                applyFrameRate(if (vm.batterySaver) FrameRate.STANDARD else rate)
            }
            // Light, dark or follow the system; the game's colors or Material You.
            val prefs = vm.state.settings
            val dark = com.monsterquest.app.ui.isDarkTheme(prefs.themeMode)
            androidx.compose.runtime.LaunchedEffect(dark) {
                // Status and navigation bar icons dark on light screens, light on dark ones.
                val transparent = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(transparent)
                    else androidx.activity.SystemBarStyle.light(transparent, transparent),
                )
            }
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme(prefs.themeMode, prefs.colorStyle) {
                    AppRoot(vm)
                    // The loading screen on a fresh start (not when the screen rotates or folds).
                    var booting by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(coldStart) }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = booting,
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
                    ) {
                        com.monsterquest.app.ui.BootSplash(vm.state.hero.name) { booting = false }
                    }
                }
            }
        }
    }

    companion object {
        /** Which screen to open: "eggs", "adventures" or "quests" (from AI Widget Maker's widgets). */
        const val EXTRA_OPEN = "open"
    }
}
