package com.example.chargealarm

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

// ============================================================
//  MAIN ACTIVITY
// ============================================================
class MainActivity : AppCompatActivity() {

    private lateinit var seekBar: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var seekBarTemp: SeekBar
    private lateinit var tvLimit: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvTempLimit: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBatteryNow: TextView
    private lateinit var tvTemperature: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvSession: TextView
    private lateinit var switchAutoMode: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibrate: SwitchCompat
    private lateinit var switchSnooze: SwitchCompat
    private lateinit var switchTts: SwitchCompat
    private lateinit var switchTempWarning: SwitchCompat
    private lateinit var etTtsText: EditText
    private lateinit var prefs: SharedPreferences
    private var testPlayer: MediaPlayer? = null
    private val uiHandler = Handler(Looper.getMainLooper())

    companion object {
        const val PREFS = "settings"
        const val KEY_LIMIT = "limit"
        const val KEY_VOLUME = "volume"
        const val KEY_AUTO_MODE = "auto_mode"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_SNOOZE = "snooze_enabled"
        const val KEY_ACTIVE = "service_active"

        // New feature settings/state.
        const val KEY_TTS = "tts_enabled"
        const val KEY_TTS_TEXT = "tts_text"
        const val KEY_TEMP_WARNING = "temp_warning_enabled"
        const val KEY_TEMP_LIMIT = "temp_limit"
        const val KEY_LAST_TEMP = "last_temperature"
        const val KEY_LAST_PCT = "last_pct"
        const val KEY_LAST_SPEED = "last_speed_percent_hour"
        const val KEY_LAST_ETA = "last_eta_minutes"
        const val KEY_SESSION_START = "session_start"
        const val KEY_SESSION_START_PCT = "session_start_pct"
        const val KEY_SESSION_TARGET_AT = "session_target_at"
        const val KEY_SESSION_TARGET_RECORDED = "session_target_recorded"
        const val KEY_HISTORY = "charging_history"

        const val DEFAULT_TTS_TEXT =
            "Baterai sudah {percent} persen, silakan cabut charger."
        const val REQ_NOTIF = 100
        const val TAG = "ChargeAlarm"
        const val HISTORY_LIMIT = 20
    }

    private val refreshUi = object : Runnable {
        override fun run() {
            if (::prefs.isInitialized) {
                try {
                    updateStatusUI()
                    updateLiveInfo()
                } catch (e: Throwable) {
                    Log.e(TAG, "refreshUi error", e)
                }
            }
            uiHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

            seekBar = findViewById(R.id.seekBar)
            seekBarVolume = findViewById(R.id.seekBarVolume)
            seekBarTemp = findViewById(R.id.seekBarTemp)
            tvLimit = findViewById(R.id.tvLimit)
            tvVolume = findViewById(R.id.tvVolume)
            tvTempLimit = findViewById(R.id.tvTempLimit)
            tvStatus = findViewById(R.id.tvStatus)
            tvBatteryNow = findViewById(R.id.tvBatteryNow)
            tvTemperature = findViewById(R.id.tvTemperature)
            tvSpeed = findViewById(R.id.tvSpeed)
            tvEta = findViewById(R.id.tvEta)
            tvSession = findViewById(R.id.tvSession)
            switchAutoMode = findViewById(R.id.switchAutoMode)
            switchSound = findViewById(R.id.switchSound)
            switchVibrate = findViewById(R.id.switchVibrate)
            switchSnooze = findViewById(R.id.switchSnooze)
            switchTts = findViewById(R.id.switchTts)
            switchTempWarning = findViewById(R.id.switchTempWarning)
            etTtsText = findViewById(R.id.etTtsText)

            val btnStart = findViewById<Button>(R.id.btnStart)
            val btnStop = findViewById<Button>(R.id.btnStop)
            val btnBattOpt = findViewById<Button>(R.id.btnBattOpt)
            val btnTestSound = findViewById<Button>(R.id.btnTestSound)
            val btnHistory = findViewById<Button>(R.id.btnHistory)
            val btnClearHistory = findViewById<Button>(R.id.btnClearHistory)

            requestNotificationPermission()

            val savedLimit = prefs.getInt(KEY_LIMIT, 80).coerceIn(50, 100)
            seekBar.progress = savedLimit - 50
            tvLimit.text = "Batas Baterai: $savedLimit%"

            val savedVolume = prefs.getInt(KEY_VOLUME, 80).coerceIn(0, 100)
            seekBarVolume.progress = savedVolume
            tvVolume.text = "🔊 Volume Alarm: $savedVolume%"

            val savedTemp = prefs.getInt(KEY_TEMP_LIMIT, 42).coerceIn(35, 50)
            seekBarTemp.progress = savedTemp - 35
            tvTempLimit.text = "Peringatan suhu: ${savedTemp}°C"

            etTtsText.setText(
                prefs.getString(KEY_TTS_TEXT, DEFAULT_TTS_TEXT) ?: DEFAULT_TTS_TEXT
            )

            switchAutoMode.isChecked = prefs.getBoolean(KEY_AUTO_MODE, true)
            switchSound.isChecked = prefs.getBoolean(KEY_SOUND, true)
            switchVibrate.isChecked = prefs.getBoolean(KEY_VIBRATE, true)
            switchSnooze.isChecked = prefs.getBoolean(KEY_SNOOZE, true)
            switchTts.isChecked = prefs.getBoolean(KEY_TTS, true)
            switchTempWarning.isChecked = prefs.getBoolean(KEY_TEMP_WARNING, true)

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val limit = progress + 50
                    tvLimit.text = "Batas Baterai: $limit%"
                    prefs.edit().putInt(KEY_LIMIT, limit).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvVolume.text = "🔊 Volume Alarm: $progress%"
                    prefs.edit().putInt(KEY_VOLUME, progress).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            seekBarTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val limit = progress + 35
                    tvTempLimit.text = "Peringatan suhu: ${limit}°C"
                    prefs.edit().putInt(KEY_TEMP_LIMIT, limit).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })

            switchAutoMode.setOnCheckedChangeListener { _, enabled ->
                prefs.edit().putBoolean(KEY_AUTO_MODE, enabled).apply()
                if (enabled) {
                    val charging = isCurrentlyCharging()
                    if (charging) {
                        BatteryMonitorService.start(this)
                        prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
                    }
                    Toast.makeText(
                        this,
                        if (charging) "Auto-mode ON 🔌 Monitoring aktif"
                        else "Auto-mode ON 🔌 Menunggu charger",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, "Auto-mode OFF", Toast.LENGTH_SHORT).show()
                }
                updateStatusUI()
            }

            switchSound.setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(KEY_SOUND, v).apply()
            }
            switchVibrate.setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(KEY_VIBRATE, v).apply()
            }
            switchSnooze.setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(KEY_SNOOZE, v).apply()
            }
            switchTts.setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(KEY_TTS, v).apply()
            }
            switchTempWarning.setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(KEY_TEMP_WARNING, v).apply()
            }

            etTtsText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) saveTtsText()
            }

            btnTestSound.setOnClickListener {
                playTestSound(3000)
            }

            btnStart.setOnClickListener {
                try {
                    saveTtsText()
                    BatteryMonitorService.start(this)
                    prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
                    updateStatusUI()
                    Toast.makeText(this, "Alarm aktif ✅", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    logCrash("btnStart", e)
                    Toast.makeText(
                        this,
                        "Gagal mengaktifkan alarm: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            btnStop.setOnClickListener {
                try {
                    BatteryMonitorService.stop(this)
                    prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
                    updateStatusUI()
                    Toast.makeText(this, "Alarm dimatikan", Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    logCrash("btnStop", e)
                    Toast.makeText(
                        this,
                        "Gagal mematikan alarm: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            btnBattOpt.setOnClickListener {
                try {
                    requestIgnoreBatteryOptimization()
                } catch (e: Throwable) {
                    logCrash("btnBattOpt", e)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            btnHistory.setOnClickListener { showHistoryDialog() }

            btnClearHistory.setOnClickListener {
                prefs.edit().remove(KEY_HISTORY).apply()
                Toast.makeText(this, "Riwayat charging dihapus", Toast.LENGTH_SHORT).show()
            }

            updateStatusUI()
            updateLiveInfo()
            uiHandler.post(refreshUi)
            Log.i(TAG, "onCreate selesai tanpa error")
        } catch (e: Throwable) {
            logCrash("onCreate", e)
            Toast.makeText(
                this,
                "CRASH: ${e.javaClass.simpleName}\n${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun saveTtsText() {
        if (!::etTtsText.isInitialized) return
        val text = etTtsText.text?.toString()?.trim().orEmpty()
        prefs.edit().putString(
            KEY_TTS_TEXT,
            if (text.isBlank()) DEFAULT_TTS_TEXT else text.take(200)
        ).apply()
    }

    private fun isCurrentlyCharging(): Boolean {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) false else {
                val status = intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membaca charging state", e)
            false
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateStatusUI()
            updateLiveInfo()
        } catch (e: Throwable) {
            logCrash("onResume", e)
        }
    }

    override fun onPause() {
        saveTtsText()
        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(refreshUi)
        try {
            testPlayer?.stop()
            testPlayer?.release()
        } catch (_: Exception) {}
        testPlayer = null
        super.onDestroy()
    }

    private fun updateStatusUI() {
        if (!::prefs.isInitialized) return
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val autoMode = prefs.getBoolean(KEY_AUTO_MODE, true)
        tvStatus.text = when {
            active && autoMode -> "● Aktif • Auto charging"
            active -> "● Aktif • Monitoring"
            autoMode -> "● Siaga • Menunggu charger"
            else -> "● Tidak aktif"
        }
        tvStatus.setTextColor(
            when {
                active -> getColorCompat(android.R.color.holo_green_dark)
                autoMode -> getColorCompat(android.R.color.holo_orange_dark)
                else -> getColorCompat(android.R.color.darker_gray)
            }
        )
    }

    private fun updateLiveInfo() {
        if (!::prefs.isInitialized) return

        val pct = prefs.getInt(KEY_LAST_PCT, -1)
        val tempTenths = prefs.getInt(KEY_LAST_TEMP, Int.MIN_VALUE)
        val speed = prefs.getFloat(KEY_LAST_SPEED, 0f)
        val eta = prefs.getLong(KEY_LAST_ETA, -1L)
        val sessionStart = prefs.getLong(KEY_SESSION_START, 0L)
        val targetAt = prefs.getLong(KEY_SESSION_TARGET_AT, 0L)

        tvBatteryNow.text = if (pct >= 0) "$pct%" else "--%"
        tvTemperature.text = if (tempTenths != Int.MIN_VALUE) {
            val temp = tempTenths / 10f
            "🌡️ %.1f°C".format(Locale.US, temp)
        } else "🌡️ --°C"

        tvSpeed.text = if (speed > 0.05f) {
            "⚡ %.1f%% / jam".format(Locale.US, speed)
        } else {
            "⚡ Menghitung kecepatan…"
        }

        tvEta.text = when {
            targetAt > 0L -> "✓ Target tercapai"
            eta >= 0L -> "⏱️ ETA ${formatDuration(eta * 60_000L)}"
            else -> "⏱️ ETA --"
        }

        tvSession.text = when {
            sessionStart <= 0L -> "Belum ada sesi charging aktif"
            targetAt > 0L -> {
                val d = max(0L, targetAt - sessionStart)
                "Sesi: target tercapai • durasi ${formatDuration(d)}"
            }
            else -> {
                val d = max(0L, System.currentTimeMillis() - sessionStart)
                "Sesi berjalan • ${formatDuration(d)}"
            }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0L)
        val hours = totalMin / 60
        val mins = totalMin % 60
        return if (hours > 0) "${hours}j ${mins}m" else "${mins}m"
    }

    private fun getColorCompat(colorRes: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(colorRes)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(colorRes)
        }
    }

    private fun showHistoryDialog() {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 24, 36, 12)
        }

        if (arr.length() == 0) {
            container.addView(TextView(this).apply {
                text = "Belum ada riwayat charging."
                textSize = 15f
                setPadding(0, 20, 0, 20)
            })
        } else {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = o.optLong("start", 0)
                val target = o.optLong("targetAt", 0)
                val end = o.optLong("end", 0)
                val startPct = o.optInt("startPct", 0)
                val targetPct = o.optInt("targetPct", prefs.getInt(KEY_LIMIT, 80))
                val reached = o.optBoolean("targetReached", false)
                val duration = o.optLong("duration", 0)

                val line = TextView(this).apply {
                    text = buildString {
                        append("${formatDate(start)}\n")
                        append("🔋 $startPct% → ")
                        append(if (reached) "$targetPct% ✓" else "belum $targetPct%")
                        append("\n⏱️ ${formatDuration(duration)}")
                        if (target > 0) append(" • target ${formatDate(target)}")
                        if (end > 0 && !reached) append(" • selesai ${formatDate(end)}")
                    }
                    textSize = 14f
                    setTextColor(getColorCompat(android.R.color.black))
                    setPadding(0, 12, 0, 12)
                }
                container.addView(line)
                if (i < arr.length() - 1) {
                    container.addView(View(this).apply {
                        setBackgroundColor(0xFFE5E7EB.toInt())
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                        )
                    })
                }
            }
        }

        val scroll = ScrollView(this).apply { addView(container) }
        AlertDialog.Builder(this)
            .setTitle("📊 Riwayat Charging")
            .setView(scroll)
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun formatDate(ms: Long): String {
        if (ms <= 0) return "--"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    }

    private fun playTestSound(durationMs: Long) {
        try {
            testPlayer?.let {
                try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
                try { it.release() } catch (_: Exception) {}
            }
            testPlayer = null

            val volumePercent = prefs.getInt(KEY_VOLUME, 80).coerceIn(0, 100)
            val volume = volumePercent / 100f
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val player = MediaPlayer()
            player.setAudioAttributes(attributes)
            val customId = resources.getIdentifier("alarm", "raw", packageName)

            if (customId != 0) {
                resources.openRawResourceFd(customId)?.let { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (uri == null) throw IllegalStateException("Tidak ada suara alarm")
                player.setDataSource(this, uri)
            }

            player.setVolume(volume, volume)
            player.prepare()
            player.start()
            testPlayer = player

            Toast.makeText(this, "🔊 Test suara: $volumePercent%", Toast.LENGTH_SHORT).show()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    testPlayer?.let {
                        try { if (it.isPlaying) it.stop() } catch (_: Exception) {}
                        try { it.release() } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
                testPlayer = null
            }, durationMs)
        } catch (e: Throwable) {
            Log.e(TAG, "playTestSound gagal", e)
            Toast.makeText(this, "Gagal memutar suara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQ_NOTIF
            )
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "✅ Optimasi baterai sudah dimatikan", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Toast.makeText(this, "Pilih 'Allow' untuk menonaktifkan optimasi", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
    }

    private fun logCrash(tag: String, e: Throwable) {
        Log.e(TAG, "CRASH di $tag", e)
        try {
            val f = File(getExternalFilesDir(null), "crash.txt")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText(
                """
                ============================================
                Waktu: $time
                Lokasi: $tag
                Android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})
                Device: ${Build.MANUFACTURER} ${Build.MODEL}
                ============================================
                ${e.javaClass.name}: ${e.message}
                ${e.stackTraceToString()}

                """.trimIndent() + "\n\n"
            )
        } catch (ex: Exception) {
            Log.e(TAG, "Gagal tulis crash.txt", ex)
        }
    }
}

// ============================================================
//  FOREGROUND SERVICE
// ============================================================
class BatteryMonitorService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var alarmTriggered = false
    private var wasCharging = false
    private var temperatureWarningTriggered = false

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val ttsHandler = Handler(Looper.getMainLooper())
    private val ttsRepeatRunnable = object : Runnable {
        override fun run() {
            if (!alarmTriggered || !ttsReady || !isTtsEnabled() || !isCurrentlyCharging()) return
            speakAlarmText()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                if (intent.action != Intent.ACTION_BATTERY_CHANGED) return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (level < 0 || scale <= 0) return

                val pct = (level * 100f / scale.toFloat()).toInt().coerceIn(0, 100)
                val status = intent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0

                val tempTenths = intent.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Int.MIN_VALUE
                )
                val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                val limit = prefs.getInt(MainActivity.KEY_LIMIT, 80).coerceIn(50, 100)

                prefs.edit()
                    .putInt(MainActivity.KEY_LAST_PCT, pct)
                    .apply()

                if (tempTenths != Int.MIN_VALUE) {
                    prefs.edit().putInt(MainActivity.KEY_LAST_TEMP, tempTenths).apply()
                }

                // ===== CHARGER DICABUT =====
                if (wasCharging && !isCharging) {
                    Log.i(MainActivity.TAG, "🔌 Charging berhenti → hentikan alarm")
                    alarmTriggered = false
                    temperatureWarningTriggered = false
                    stopAlarm()
                    stopTts()

                    finishChargingSession(pct)

                    if (prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)) {
                        prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
                        wasCharging = false
                        stopSelf()
                        return
                    }
                }

                // ===== CHARGER BARU MASUK =====
                if (!wasCharging && isCharging) {
                    Log.i(MainActivity.TAG, "⚡ Charging dimulai")
                    alarmTriggered = false
                    temperatureWarningTriggered = false
                    beginChargingSessionIfNeeded(pct)
                }

                if (isCharging) {
                    beginChargingSessionIfNeeded(pct)
                    updateChargingSpeed(pct, limit)

                    // ===== TEMPERATURE WARNING =====
                    if (tempTenths != Int.MIN_VALUE) {
                        val tempC = tempTenths / 10f
                        val tempLimit = prefs.getInt(
                            MainActivity.KEY_TEMP_LIMIT, 42
                        ).coerceIn(35, 50)
                        val warningEnabled = prefs.getBoolean(
                            MainActivity.KEY_TEMP_WARNING, true
                        )

                        if (warningEnabled && tempC >= tempLimit) {
                            if (!temperatureWarningTriggered) {
                                temperatureWarningTriggered = true
                                showTemperatureWarning(tempC, tempLimit)
                            }
                        } else if (tempC <= tempLimit - 1.0f) {
                            temperatureWarningTriggered = false
                        }
                    }

                    // ===== TARGET BATTERY =====
                    if (pct >= limit && !alarmTriggered) {
                        Log.i(MainActivity.TAG, "🔔 Target tercapai: $pct% >= $limit%")
                        alarmTriggered = true
                        recordTargetReached(pct)
                        triggerAlarm(pct)
                    }
                }

                wasCharging = isCharging
                updateForegroundNotif(pct, isCharging, limit, tempTenths)
            } catch (e: Throwable) {
                Log.e(MainActivity.TAG, "Error batteryReceiver", e)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "charge_alarm_channel"
        const val CHANNEL_SILENT = "charge_alarm_silent"
        const val CHANNEL_TEMP = "charge_alarm_temperature"
        const val NOTIF_ID_FG = 1
        const val NOTIF_ID_ALARM = 2
        const val NOTIF_ID_TEMP = 3

        fun start(context: Context) {
            try {
                val intent = Intent(context, BatteryMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Throwable) {
                Log.e(MainActivity.TAG, "Gagal start BatteryMonitorService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                if (!prefs.getBoolean(MainActivity.KEY_ACTIVE, false)) {
                    context.stopService(Intent(context, BatteryMonitorService::class.java))
                    return
                }

                val stopIntent = Intent(context, BatteryMonitorService::class.java).apply {
                    action = "USER_STOP"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(stopIntent)
                } else {
                    context.startService(stopIntent)
                }
            } catch (e: Throwable) {
                Log.e(MainActivity.TAG, "Gagal mengirim USER_STOP", e)
                try { context.stopService(Intent(context, BatteryMonitorService::class.java)) } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannels()
            startForeground(
                NOTIF_ID_FG,
                buildForegroundNotification("Memeriksa status charging...")
            )

            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale("id", "ID")
                    tts?.setSpeechRate(0.95f)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            if (utteranceId == "charge_alarm_tts") {
                                ttsHandler.post { scheduleNextTts() }
                            }
                        }
                        override fun onError(utteranceId: String?) {}
                    })
                    if (alarmTriggered && isTtsEnabled()) {
                        speakAlarmText()
                    }
                }
            }

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val initialBatteryIntent = registerReceiver(null, filter)
            registerReceiver(batteryReceiver, filter)

            initialBatteryIntent?.let {
                batteryReceiver.onReceive(this, it)
            }

            Log.i(MainActivity.TAG, "BatteryMonitorService started")
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error Service.onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                "USER_STOP" -> {
                    val batteryIntent = registerReceiver(
                        null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                    )
                    val pct = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val endPct = if (pct >= 0 && scale > 0) {
                        (pct * 100f / scale.toFloat()).toInt().coerceIn(0, 100)
                    } else {
                        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                            .getInt(MainActivity.KEY_LAST_PCT, 0)
                    }
                    finishChargingSession(endPct)
                    stopAlarm()
                    stopTts()
                    getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
                    stopSelf()
                    return START_NOT_STICKY
                }

                "SNOOZE_STOP" -> {
                    stopAlarm()
                    stopTts()
                    alarmTriggered = true
                }
                "SNOOZE_TRIGGER" -> {
                    alarmTriggered = false
                    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                    val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val limit = getSharedPreferences(
                        MainActivity.PREFS, MODE_PRIVATE
                    ).getInt(MainActivity.KEY_LIMIT, 80)

                    if (bm.isCharging && pct >= limit) {
                        alarmTriggered = true
                        triggerAlarm(pct)
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error Service.onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
        stopAlarm()
        stopTts()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val alarmCh = NotificationChannel(
                CHANNEL_ID,
                "Charge Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm saat baterai mencapai batas"
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val silentCh = NotificationChannel(
                CHANNEL_SILENT,
                "Status Monitoring",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Notifikasi senyap status monitoring"
                setShowBadge(false)
            }

            val tempCh = NotificationChannel(
                CHANNEL_TEMP,
                "Peringatan Suhu",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Peringatan ketika temperatur baterai tinggi"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            mgr.createNotificationChannel(alarmCh)
            mgr.createNotificationChannel(silentCh)
            mgr.createNotificationChannel(tempCh)
        }
    }

    private fun triggerAlarm(pct: Int) {
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            val playSound = prefs.getBoolean(MainActivity.KEY_SOUND, true)
            val vibrate = prefs.getBoolean(MainActivity.KEY_VIBRATE, true)
            val snoozeOn = prefs.getBoolean(MainActivity.KEY_SNOOZE, true)

            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIF_ID_ALARM, buildAlarmNotification(pct, snoozeOn))

            if (playSound) playAlarmSound()
            if (vibrate) vibrateAlarm()
            if (isTtsEnabled()) startTtsLoop()
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error di triggerAlarm", e)
        }
    }

    private fun isTtsEnabled(): Boolean {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            .getBoolean(MainActivity.KEY_TTS, true)
    }

    private fun startTtsLoop() {
        if (!ttsReady || !isTtsEnabled()) return
        ttsHandler.removeCallbacks(ttsRepeatRunnable)
        speakAlarmText()
    }

    private fun speakAlarmText() {
        if (!alarmTriggered || !ttsReady || !isTtsEnabled() || !isCurrentlyCharging()) return

        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val pct = prefs.getInt(MainActivity.KEY_LAST_PCT, 0)
        var text = prefs.getString(
            MainActivity.KEY_TTS_TEXT,
            MainActivity.DEFAULT_TTS_TEXT
        ) ?: MainActivity.DEFAULT_TTS_TEXT

        text = text.replace("{percent}", pct.toString())
        text = text.replace("{batas}", prefs.getInt(MainActivity.KEY_LIMIT, 80).toString())
        if (text.isBlank()) {
            text = MainActivity.DEFAULT_TTS_TEXT.replace("{percent}", pct.toString())
        }

        try {
            tts?.speak(text.take(200), TextToSpeech.QUEUE_FLUSH, null, "charge_alarm_tts")
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "TTS speak gagal", e)
            return
        }
    }

    private fun scheduleNextTts() {
        ttsHandler.removeCallbacks(ttsRepeatRunnable)
        ttsHandler.postDelayed(ttsRepeatRunnable, 2500L)
    }

    private fun stopTts() {
        ttsHandler.removeCallbacks(ttsRepeatRunnable)
        try { tts?.stop() } catch (_: Exception) {}
    }

    private fun playAlarmSound() {
        try {
            stopMediaPlayerOnly()

            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            val volumePercent = prefs.getInt(MainActivity.KEY_VOLUME, 80).coerceIn(0, 100)
            val volume = volumePercent / 100f

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val customId = resources.getIdentifier("alarm", "raw", packageName)
            val player = MediaPlayer()
            player.setAudioAttributes(attributes)

            if (customId != 0) {
                resources.openRawResourceFd(customId)?.let { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                }
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                if (uri == null) throw IllegalStateException("Tidak ada ringtone alarm")
                player.setDataSource(this, uri)
            }

            player.setVolume(volume, volume)
            player.isLooping = true
            player.setOnErrorListener { mp, _, _ ->
                try { mp.reset(); mp.release() } catch (_: Exception) {}
                if (mediaPlayer === mp) mediaPlayer = null
                true
            }
            player.prepare()
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "MediaPlayer alarm gagal", e)
            try {
                val volumePercent = getSharedPreferences(
                    MainActivity.PREFS, MODE_PRIVATE
                ).getInt(MainActivity.KEY_VOLUME, 80).coerceIn(0, 100)
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, volumePercent)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30_000)
            } catch (fallbackError: Exception) {
                Log.e(MainActivity.TAG, "ToneGenerator juga gagal", fallbackError)
            }
        }
    }

    private fun stopMediaPlayerOnly() {
        try {
            mediaPlayer?.let { player ->
                try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
                try { player.reset() } catch (_: Exception) {}
                try { player.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun vibrateAlarm() {
        try {
            val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Error vibrateAlarm", e)
        }
    }

    private fun stopAlarm() {
        stopMediaPlayerOnly()
        try { toneGenerator?.stopTone() } catch (_: Exception) {}
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        try {
            (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel()
        } catch (_: Exception) {}
        try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(NOTIF_ID_ALARM)
        } catch (_: Exception) {}
    }

    private fun buildForegroundNotification(text: String): Notification {
        val stopPi = actionPendingIntent("com.example.chargealarm.STOP_ALARM", 1)
        return NotificationCompat.Builder(this, CHANNEL_SILENT)
            .setContentTitle("🔋 Charge Alarm aktif")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Matikan",
                stopPi
            )
            .build()
    }

    private fun buildAlarmNotification(pct: Int, withSnooze: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔔 Baterai $pct% — Cabut Charger!")
            .setContentText("Baterai sudah mencapai batas. Alarm + suara TTS akan berulang.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Matikan",
                actionPendingIntent("com.example.chargealarm.STOP_ALARM", 10)
            )
        if (withSnooze) {
            builder.addAction(
                android.R.drawable.ic_popup_reminder,
                "Tunda 1 mnt",
                actionPendingIntent("com.example.chargealarm.SNOOZE_ALARM", 11)
            )
        }
        return builder.build()
    }

    private fun showTemperatureWarning(tempC: Float, limitC: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val text = "Temperatur baterai %.1f°C. Hentikan/kurangi charging jika terasa terlalu panas."
            .format(Locale.US, tempC)

        manager.notify(
            NOTIF_ID_TEMP,
            NotificationCompat.Builder(this, CHANNEL_TEMP)
                .setContentTitle("🌡️ Peringatan temperatur tinggi")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setAutoCancel(false)
                .setOngoing(false)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build()
        )
        Log.w(MainActivity.TAG, "Temperature warning: $tempC°C >= $limitC°C")
    }

    private fun updateForegroundNotif(
        pct: Int,
        charging: Boolean,
        limit: Int,
        tempTenths: Int
    ) {
        try {
            val status = if (charging) "⚡ Charging" else "🔌 Tidak charging"
            val tempText = if (tempTenths != Int.MIN_VALUE) {
                " • %.1f°C".format(Locale.US, tempTenths / 10f)
            } else ""
            val text = "$status • $pct% / $limit%$tempText"
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIF_ID_FG, buildForegroundNotification(text))
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "Error updateForegroundNotif", e)
        }
    }

    private fun actionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val i = Intent(this, AlarmActionReceiver::class.java).apply { this.action = action }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(this, reqCode, i, flags)
    }

    private fun isCurrentlyCharging(): Boolean {
        return try {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i == null) false else {
                val status = i.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun beginChargingSessionIfNeeded(pct: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val currentStart = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        if (currentStart <= 0L) {
            prefs.edit()
                .putLong(MainActivity.KEY_SESSION_START, System.currentTimeMillis())
                .putInt(MainActivity.KEY_SESSION_START_PCT, pct)
                .putLong(MainActivity.KEY_SESSION_TARGET_AT, 0L)
                .putBoolean(MainActivity.KEY_SESSION_TARGET_RECORDED, false)
                .apply()
            Log.i(MainActivity.TAG, "📊 Charging session started at $pct%")
        }
    }

    private fun updateChargingSpeed(pct: Int, limit: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val start = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        val startPct = prefs.getInt(MainActivity.KEY_SESSION_START_PCT, pct)
        if (start <= 0L) return

        val elapsedHours = (System.currentTimeMillis() - start).coerceAtLeast(1000L) / 3_600_000f
        val deltaPct = pct - startPct
        val speed = if (elapsedHours > 0.008f && deltaPct > 0) {
            deltaPct / elapsedHours
        } else 0f

        var etaMinutes = -1L
        if (speed > 0.05f && pct < limit) {
            etaMinutes = (((limit - pct) / speed) * 60f).toLong().coerceAtLeast(1L)
        }

        prefs.edit()
            .putFloat(MainActivity.KEY_LAST_SPEED, speed)
            .putLong(MainActivity.KEY_LAST_ETA, etaMinutes)
            .apply()
    }

    private fun recordTargetReached(pct: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(MainActivity.KEY_SESSION_TARGET_RECORDED, false)) return

        val now = System.currentTimeMillis()
        val start = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        val startPct = prefs.getInt(MainActivity.KEY_SESSION_START_PCT, pct)
        val targetPct = prefs.getInt(MainActivity.KEY_LIMIT, 80)
        val duration = if (start > 0L) (now - start).coerceAtLeast(0L) else 0L

        prefs.edit()
            .putLong(MainActivity.KEY_SESSION_TARGET_AT, now)
            .putBoolean(MainActivity.KEY_SESSION_TARGET_RECORDED, true)
            .apply()

        // Simpan langsung ketika target tercapai supaya history tetap ada
        // walaupun user menekan STOP tanpa mencabut charger.
        if (start > 0L) {
            val item = JSONObject().apply {
                put("start", start)
                put("end", now)
                put("targetAt", now)
                put("startPct", startPct)
                put("targetPct", targetPct)
                put("endPct", pct)
                put("targetReached", true)
                put("duration", duration)
            }

            val old = try {
                JSONArray(prefs.getString(MainActivity.KEY_HISTORY, "[]") ?: "[]")
            } catch (_: Exception) {
                JSONArray()
            }
            val merged = JSONArray()
            merged.put(item)
            for (i in 0 until minOf(old.length(), MainActivity.HISTORY_LIMIT - 1)) {
                merged.put(old.opt(i))
            }
            prefs.edit().putString(MainActivity.KEY_HISTORY, merged.toString()).apply()
        }

        Log.i(MainActivity.TAG, "📊 Target tercapai, session history saved")
    }

    private fun finishChargingSession(endPct: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val start = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        if (start <= 0L) return

        val targetAt = prefs.getLong(MainActivity.KEY_SESSION_TARGET_AT, 0L)
        val end = System.currentTimeMillis()
        val alreadyRecorded = prefs.getBoolean(MainActivity.KEY_SESSION_TARGET_RECORDED, false)

        // Target sessions are already stored at the moment target is reached.
        if (alreadyRecorded && targetAt > 0L) {
            prefs.edit()
                .remove(MainActivity.KEY_SESSION_START)
                .remove(MainActivity.KEY_SESSION_START_PCT)
                .remove(MainActivity.KEY_SESSION_TARGET_AT)
                .remove(MainActivity.KEY_SESSION_TARGET_RECORDED)
                .putFloat(MainActivity.KEY_LAST_SPEED, 0f)
                .putLong(MainActivity.KEY_LAST_ETA, -1L)
                .apply()
            return
        }

        val duration = end - start
        val startPct = prefs.getInt(MainActivity.KEY_SESSION_START_PCT, endPct)
        val targetPct = prefs.getInt(MainActivity.KEY_LIMIT, 80)

        val item = JSONObject().apply {
            put("start", start)
            put("end", end)
            put("targetAt", targetAt)
            put("startPct", startPct)
            put("targetPct", targetPct)
            put("endPct", endPct)
            put("targetReached", targetAt > 0L)
            put("duration", duration.coerceAtLeast(0L))
        }

        val old = try {
            JSONArray(prefs.getString(MainActivity.KEY_HISTORY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val merged = JSONArray()
        merged.put(item)
        for (i in 0 until minOf(old.length(), MainActivity.HISTORY_LIMIT - 1)) {
            merged.put(old.opt(i))
        }

        prefs.edit()
            .putString(MainActivity.KEY_HISTORY, merged.toString())
            .remove(MainActivity.KEY_SESSION_START)
            .remove(MainActivity.KEY_SESSION_START_PCT)
            .remove(MainActivity.KEY_SESSION_TARGET_AT)
            .remove(MainActivity.KEY_SESSION_TARGET_RECORDED)
            .putFloat(MainActivity.KEY_LAST_SPEED, 0f)
            .putLong(MainActivity.KEY_LAST_ETA, -1L)
            .apply()

        Log.i(MainActivity.TAG, "📊 Charging session saved: ${item}")
    }
}

// ============================================================
//  ALARM ACTION RECEIVER
// ============================================================
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                "com.example.chargealarm.STOP_ALARM" -> {
                    Log.i(MainActivity.TAG, "User menekan Matikan")
                    BatteryMonitorService.stop(context)
                    context.getSharedPreferences(
                        MainActivity.PREFS, Context.MODE_PRIVATE
                    ).edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
                }

                "com.example.chargealarm.SNOOZE_ALARM" -> {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val i = Intent(context, SnoozeReceiver::class.java)
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    } else PendingIntent.FLAG_UPDATE_CURRENT
                    val pi = PendingIntent.getBroadcast(context, 20, i, flags)
                    val triggerAt = System.currentTimeMillis() + 60_000L

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }

                    val svcIntent = Intent(context, BatteryMonitorService::class.java)
                    svcIntent.action = "SNOOZE_STOP"
                    try {
                        context.startService(svcIntent)
                    } catch (e: Exception) {
                        BatteryMonitorService.start(context)
                        try { context.startService(svcIntent) } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error di AlarmActionReceiver", e)
        }
    }
}

// ============================================================
//  SNOOZE RECEIVER
// ============================================================
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val batteryIntent = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ) ?: return

            val status = batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0

            if (!charging) {
                BatteryMonitorService.stop(context)
                return
            }

            val i = Intent(context, BatteryMonitorService::class.java).apply {
                action = "SNOOZE_TRIGGER"
            }
            BatteryMonitorService.start(context)
            try { context.startService(i) } catch (e: Exception) {
                Log.e(MainActivity.TAG, "Gagal trigger snooze", e)
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "SnoozeReceiver error", e)
        }
    }
}

// ============================================================
//  POWER CONNECTION RECEIVER
// ============================================================
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val autoMode = prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)
            if (!autoMode) return

            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    Log.i(MainActivity.TAG, "⚡ Charger terhubung → start monitoring")
                    BatteryMonitorService.start(context)
                    prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, true).apply()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    Log.i(MainActivity.TAG, "🔌 Charger dicabut → stop monitoring")
                    BatteryMonitorService.stop(context)
                    prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
                }
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "PowerConnectionReceiver error", e)
        }
    }
}

// ============================================================
//  BOOT RECEIVER
// ============================================================
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

            val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val autoMode = prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)
            val manuallyActive = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)

            val batteryIntent = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val charging = if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                )
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
            } else false

            if (manuallyActive || (autoMode && charging)) {
                Log.i(MainActivity.TAG, "BOOT → start monitoring. auto=$autoMode charging=$charging")
                BatteryMonitorService.start(context)
                prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, true).apply()
            } else {
                Log.i(MainActivity.TAG, "BOOT → monitoring tidak diperlukan")
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "BootReceiver error", e)
        }
    }
}
