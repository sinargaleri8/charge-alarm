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
import android.util.Log
import android.view.LayoutInflater
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
import java.util.concurrent.TimeUnit

// ============================================================
// MAIN ACTIVITY
// ============================================================
class MainActivity : AppCompatActivity() {

    private lateinit var seekBar: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var seekBarTtsInterval: SeekBar
    private lateinit var seekBarTemp: SeekBar
    private lateinit var tvLimit: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvBattery: TextView
    private lateinit var tvChargeState: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvTtsInterval: TextView
    private lateinit var tvTempLimit: TextView
    private lateinit var tvStatusPill: TextView
    private lateinit var editTts: EditText
    private lateinit var switchAutoMode: SwitchCompat
    private lateinit var switchSound: SwitchCompat
    private lateinit var switchVibrate: SwitchCompat
    private lateinit var switchSnooze: SwitchCompat
    private lateinit var switchTts: SwitchCompat
    private lateinit var switchTempWarning: SwitchCompat
    private lateinit var batteryProgress: ProgressBar
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

        const val KEY_TTS_ENABLED = "tts_enabled"
        const val KEY_TTS_TEXT = "tts_text"
        const val KEY_TTS_INTERVAL = "tts_interval"
        const val KEY_TEMP_WARNING = "temp_warning"
        const val KEY_TEMP_LIMIT = "temp_limit"

        const val KEY_CURRENT_LEVEL = "current_level"
        const val KEY_CURRENT_TEMP = "current_temp"
        const val KEY_CURRENT_CHARGING = "current_charging"
        const val KEY_CHARGE_SPEED = "charge_speed"
        const val KEY_ETA_MINUTES = "eta_minutes"

        const val KEY_SESSION_START = "session_start"
        const val KEY_SESSION_START_LEVEL = "session_start_level"
        const val KEY_SESSION_TARGET_TIME = "session_target_time"
        const val KEY_SESSION_TARGET_LEVEL = "session_target_level"
        const val KEY_SESSION_ACTIVE = "session_active"
        const val KEY_HISTORY = "charging_history"

        const val REQ_NOTIF = 100
        const val TAG = "ChargeAlarm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

            bindViews()
            loadSettings()
            setupListeners()
            requestNotificationPermission()
            updateStatusUI()

            Log.i(TAG, "UI Charge Alarm loaded")
        } catch (e: Throwable) {
            logCrash("onCreate", e)
            Toast.makeText(this, "Gagal membuka aplikasi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun bindViews() {
        seekBar = findViewById(R.id.seekBar)
        seekBarVolume = findViewById(R.id.seekBarVolume)
        seekBarTtsInterval = findViewById(R.id.seekBarTtsInterval)
        seekBarTemp = findViewById(R.id.seekBarTemp)
        tvLimit = findViewById(R.id.tvLimit)
        tvVolume = findViewById(R.id.tvVolume)
        tvStatus = findViewById(R.id.tvStatusPill)
        tvStatusPill = findViewById(R.id.tvStatusPill)
        tvBattery = findViewById(R.id.tvBattery)
        tvChargeState = findViewById(R.id.tvChargeState)
        tvEta = findViewById(R.id.tvEta)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvTemp = findViewById(R.id.tvTemp)
        tvTtsInterval = findViewById(R.id.tvTtsInterval)
        tvTempLimit = findViewById(R.id.tvTempLimit)
        editTts = findViewById(R.id.editTts)
        switchAutoMode = findViewById(R.id.switchAutoMode)
        switchSound = findViewById(R.id.switchSound)
        switchVibrate = findViewById(R.id.switchVibrate)
        switchSnooze = findViewById(R.id.switchSnooze)
        switchTts = findViewById(R.id.switchTts)
        switchTempWarning = findViewById(R.id.switchTempWarning)
        batteryProgress = findViewById(R.id.batteryProgress)
    }

    private fun loadSettings() {
        val limit = prefs.getInt(KEY_LIMIT, 80).coerceIn(50, 100)
        seekBar.progress = limit - 50
        tvLimit.text = "Batas baterai: $limit%"

        val volume = prefs.getInt(KEY_VOLUME, 80).coerceIn(0, 100)
        seekBarVolume.progress = volume
        tvVolume.text = "🔊 Volume alarm: $volume%"

        val interval = prefs.getInt(KEY_TTS_INTERVAL, 10).coerceIn(5, 30)
        seekBarTtsInterval.progress = interval - 5
        tvTtsInterval.text = "🔁 Ulangi setiap $interval detik"

        val tempLimit = prefs.getInt(KEY_TEMP_LIMIT, 45).coerceIn(40, 55)
        seekBarTemp.progress = tempLimit - 40
        tvTempLimit.text = "Batas suhu: ${tempLimit}°C"

        switchAutoMode.isChecked = prefs.getBoolean(KEY_AUTO_MODE, true)
        switchSound.isChecked = prefs.getBoolean(KEY_SOUND, true)
        switchVibrate.isChecked = prefs.getBoolean(KEY_VIBRATE, true)
        switchSnooze.isChecked = prefs.getBoolean(KEY_SNOOZE, true)
        switchTts.isChecked = prefs.getBoolean(KEY_TTS_ENABLED, true)
        switchTempWarning.isChecked = prefs.getBoolean(KEY_TEMP_WARNING, true)

        editTts.setText(
            prefs.getString(
                KEY_TTS_TEXT,
                "Baterai sudah {percent} persen, silakan cabut charger."
            )
        )

        refreshTelemetry()
    }

    private fun setupListeners() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 50
                tvLimit.text = "Batas baterai: $value%"
                prefs.edit().putInt(KEY_LIMIT, value).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvVolume.text = "🔊 Volume alarm: $progress%"
                prefs.edit().putInt(KEY_VOLUME, progress).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBarTtsInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 5
                tvTtsInterval.text = "🔁 Ulangi setiap $value detik"
                prefs.edit().putInt(KEY_TTS_INTERVAL, value).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seekBarTemp.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + 40
                tvTempLimit.text = "Batas suhu: ${value}°C"
                prefs.edit().putInt(KEY_TEMP_LIMIT, value).apply()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        switchAutoMode.setOnCheckedChangeListener { _, enabled ->
            prefs.edit().putBoolean(KEY_AUTO_MODE, enabled).apply()
            if (enabled && isCurrentlyCharging()) {
                BatteryMonitorService.start(this)
                prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
            }
            updateStatusUI()
        }

        switchSound.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(KEY_SOUND, v).apply() }
        switchVibrate.setOnCheckedChangeListener { _, v -> prefs.edit().putBoolean(KEY_VIBRATE, v).apply() }

        switchSnooze.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_SNOOZE, v).apply()
        }

        switchTts.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_TTS_ENABLED, v).apply()
        }

        switchTempWarning.setOnCheckedChangeListener { _, v ->
            prefs.edit().putBoolean(KEY_TEMP_WARNING, v).apply()
        }

        editTts.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveTtsText()
        }

        findViewById<Button>(R.id.btnTestSound).setOnClickListener {
            playTestSound(3000)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            try {
                saveTtsText()
                BatteryMonitorService.start(this)
                prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
                updateStatusUI()
                Toast.makeText(this, "Monitoring charging aktif ✅", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                logCrash("btnStart", e)
                Toast.makeText(this, "Gagal mengaktifkan: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            try {
                BatteryMonitorService.stop(this)
                prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
                updateStatusUI()
                Toast.makeText(this, "Monitoring dimatikan", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                logCrash("btnStop", e)
            }
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener { showHistory() }

        findViewById<Button>(R.id.btnBattOpt).setOnClickListener {
            try {
                requestIgnoreBatteryOptimization()
            } catch (e: Throwable) {
                logCrash("btnBattOpt", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveTtsText() {
        val text = editTts.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) prefs.edit().putString(KEY_TTS_TEXT, text).apply()
    }

    private fun refreshTelemetry() {
        val level = prefs.getInt(KEY_CURRENT_LEVEL, -1)
        val temp = prefs.getFloat(KEY_CURRENT_TEMP, -1f)
        val charging = prefs.getBoolean(KEY_CURRENT_CHARGING, false)
        val speed = prefs.getFloat(KEY_CHARGE_SPEED, 0f)
        val eta = prefs.getLong(KEY_ETA_MINUTES, -1L)
        val target = prefs.getInt(KEY_LIMIT, 80)

        if (level >= 0) {
            tvBattery.text = "$level%"
            batteryProgress.progress = level
        } else {
            tvBattery.text = "—"
        }

        tvChargeState.text = if (charging) "⚡ Sedang charging" else "🔌 Tidak sedang charging"
        tvSpeed.text = if (speed > 0.05f) String.format(Locale.US, "%.1f %%/jam", speed) else "Menghitung…"

        tvEta.text = when {
            !charging -> "Estimasi target: —"
            level >= target -> "🎯 Target $target% tercapai"
            eta >= 0 -> "Estimasi target: ${formatDuration(eta * 60_000L)} lagi"
            else -> "Estimasi target: menghitung…"
        }

        tvTemp.text = if (temp > 0f) String.format(Locale.US, "%.1f°C", temp) else "— °C"
        if (temp > 0f && temp >= prefs.getInt(KEY_TEMP_LIMIT, 45)) {
            tvTemp.setTextColor(0xFFE5484D.toInt())
        } else {
            tvTemp.setTextColor(0xFF152238.toInt())
        }
    }

    private fun updateStatusUI() {
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val autoMode = prefs.getBoolean(KEY_AUTO_MODE, true)
        val charging = prefs.getBoolean(KEY_CURRENT_CHARGING, false)

        val text = when {
            charging && active -> "● Charging"
            active && autoMode -> "● Menunggu"
            active -> "● Aktif"
            else -> "● Siap"
        }
        tvStatusPill.text = text
        tvStatus.text = text
        refreshTelemetry()
    }

    override fun onResume() {
        super.onResume()
        refreshTelemetry()
        updateStatusUI()
        uiHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing) {
                    refreshTelemetry()
                    uiHandler.postDelayed(this, 1000)
                }
            }
        }, 1000)
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        try { testPlayer?.stop() } catch (_: Exception) {}
        try { testPlayer?.release() } catch (_: Exception) {}
        testPlayer = null
    }

    private fun showHistory() {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        if (arr.length() == 0) {
            AlertDialog.Builder(this)
                .setTitle("📊 Riwayat charging")
                .setMessage("Belum ada sesi charging yang selesai.")
                .setPositiveButton("Tutup", null)
                .show()
            return
        }

        val builder = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            builder.append("⚡ Sesi ${i + 1}\n")
            builder.append("Mulai: ${o.optString("start", "-")}\n")
            builder.append("Target: ${o.optInt("startLevel", 0)}% → ${o.optInt("targetLevel", 0)}%\n")
            builder.append("Target tercapai: ${o.optString("targetTime", "Belum tercapai")}\n")
            builder.append("Durasi: ${formatDuration(o.optLong("durationMs", 0))}\n")
            builder.append("Kecepatan: ${String.format(Locale.US, "%.1f", o.optDouble("speed", 0.0))}%/jam\n")
            builder.append("Selesai: ${o.optString("end", "-")}\n\n")
        }

        AlertDialog.Builder(this)
            .setTitle("📊 Riwayat charging")
            .setMessage(builder.toString().trim())
            .setPositiveButton("Tutup", null)
            .setNegativeButton("Hapus riwayat") { _, _ ->
                prefs.edit().remove(KEY_HISTORY).apply()
            }
            .show()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "—"
        val totalMin = TimeUnit.MILLISECONDS.toMinutes(ms)
        val hours = totalMin / 60
        val mins = totalMin % 60
        return if (hours > 0) "${hours}j ${mins}m" else "${mins}m"
    }

    private fun isCurrentlyCharging(): Boolean {
        return try {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) false else {
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun playTestSound(durationMs: Long) {
        try {
            testPlayer?.release()
            testPlayer = null
            val volumePercent = prefs.getInt(KEY_VOLUME, 80).coerceIn(0, 100)
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val customId = resources.getIdentifier("alarm", "raw", packageName)
            if (customId != 0) {
                val afd = resources.openRawResourceFd(customId)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: throw IllegalStateException("Tidak ada suara alarm")
                player.setDataSource(this, uri)
            }
            val v = volumePercent / 100f
            player.setVolume(v, v)
            player.prepare()
            player.start()
            testPlayer = player
            Toast.makeText(this, "🔊 Tes suara $volumePercent%", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                try { testPlayer?.stop() } catch (_: Exception) {}
                try { testPlayer?.release() } catch (_: Exception) {}
                testPlayer = null
            }, durationMs)
        } catch (e: Throwable) {
            Toast.makeText(this, "Gagal memutar suara: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "✅ Optimasi baterai sudah dimatikan", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    private fun logCrash(tag: String, e: Throwable) {
        Log.e(TAG, "CRASH di $tag", e)
        try {
            val f = File(getExternalFilesDir(null), "crash.txt")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            f.appendText("\n[$time] $tag\n${e.stackTraceToString()}\n")
        } catch (_: Exception) {}
    }
}

// ============================================================
// FOREGROUND SERVICE
// ============================================================
class BatteryMonitorService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var alarmTriggered = false
    private var wasCharging = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private var tempWarningActive = false
    private var lastTempSpeech = 0L

    private val ttsLoop = object : Runnable {
        override fun run() {
            if (alarmTriggered) {
                speakTarget()
                val seconds = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    .getInt(MainActivity.KEY_TTS_INTERVAL, 10).coerceIn(5, 30)
                handler.postDelayed(this, seconds * 1000L)
            }
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
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0

                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val tempC = if (tempRaw != Int.MIN_VALUE) tempRaw / 10f else -1f

                val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                val limit = prefs.getInt(MainActivity.KEY_LIMIT, 80).coerceIn(50, 100)

                if (!wasCharging && isCharging) {
                    alarmTriggered = false
                    tempWarningActive = false
                    val sessionActive = prefs.getBoolean(MainActivity.KEY_SESSION_ACTIVE, false)
                    if (!sessionActive) {
                        startSession(pct, limit)
                    }
                    Log.i(MainActivity.TAG, "⚡ Charging dimulai / monitoring dilanjutkan")
                }

                if (wasCharging && !isCharging) {
                    Log.i(MainActivity.TAG, "🔌 Charger dicabut")
                    if (alarmTriggered) {
                        alarmTriggered = false
                        stopAlarm()
                    }
                    finishSession(pct)
                    prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, false).apply()
                    wasCharging = false
                    updateTelemetry(pct, tempC, false, 0f, -1L)
                    if (prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)) {
                        stopSelf()
                        return
                    }
                }

                if (isCharging) {
                    val metrics = updateChargingMetrics(pct, limit)
                    if (pct >= limit && !alarmTriggered) {
                        markTargetReached(pct)
                        alarmTriggered = true
                        triggerAlarm(pct)
                    }

                    checkTemperature(tempC, pct)
                    updateTelemetry(pct, tempC, true, metrics.first, metrics.second)
                } else {
                    updateTelemetry(pct, tempC, false, 0f, -1L)
                }

                wasCharging = isCharging
                updateForegroundNotif(pct, isCharging, limit, tempC)

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

        fun start(context: Context, action: String? = null) {
            try {
                val intent = Intent(context, BatteryMonitorService::class.java).apply {
                    if (!action.isNullOrEmpty()) this.action = action
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Throwable) {
                Log.e(MainActivity.TAG, "Gagal start service", e)
            }
        }

        fun stop(context: Context) {
            try { context.stopService(Intent(context, BatteryMonitorService::class.java)) }
            catch (e: Throwable) { Log.e(MainActivity.TAG, "Gagal stop service", e) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannels()
            startForeground(NOTIF_ID_FG, buildForegroundNotification("Memeriksa status charging…", 0, false, -1f))
            initTts()

            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val initial = registerReceiver(null, filter)
            registerReceiver(batteryReceiver, filter)
            initial?.let { batteryReceiver.onReceive(this, it) }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error Service.onCreate", e)
            stopSelf()
        }
    }

    private fun initTts() {
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("id", "ID")
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(1.0f)
                if (alarmTriggered) {
                    handler.removeCallbacks(ttsLoop)
                    handler.post(ttsLoop)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                "STOP_ALARM_ONLY" -> {
                    stopAlarm()
                    alarmTriggered = true
                }
                "SNOOZE_STOP" -> {
                    stopAlarm()
                    alarmTriggered = true
                }
                "SNOOZE_TRIGGER" -> {
                    alarmTriggered = false
                    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                    val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val limit = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                        .getInt(MainActivity.KEY_LIMIT, 80)
                    if (bm.isCharging && pct >= limit) {
                        markTargetReached(pct)
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
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        stopAlarm()
        handler.removeCallbacksAndMessages(null)
        ttsReady = false
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID_TEMP) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val alarmCh = NotificationChannel(CHANNEL_ID, "Charge Alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alarm saat baterai mencapai batas"
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val silentCh = NotificationChannel(CHANNEL_SILENT, "Status Monitoring", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Status monitoring charging"
                setShowBadge(false)
            }
            val tempCh = NotificationChannel(CHANNEL_TEMP, "Temperature Warning", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Peringatan suhu baterai tinggi"
                enableVibration(true)
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

            if (prefs.getBoolean(MainActivity.KEY_TTS_ENABLED, true)) {
                handler.removeCallbacks(ttsLoop)
                handler.post(ttsLoop)
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "Error triggerAlarm", e)
        }
    }

    private fun speakTarget() {
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_TTS_ENABLED, true) || !ttsReady) return
            val pct = prefs.getInt(MainActivity.KEY_CURRENT_LEVEL, prefs.getInt(MainActivity.KEY_LIMIT, 80))
            val target = prefs.getInt(MainActivity.KEY_LIMIT, 80)
            var text = prefs.getString(
                MainActivity.KEY_TTS_TEXT,
                "Baterai sudah {percent} persen, silakan cabut charger."
            ).orEmpty()
            text = text.replace("{percent}", pct.toString())
                .replace("{target}", target.toString())
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "charge_target")
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "TTS target gagal", e)
        }
    }

    private fun speakTemperature(tempC: Float) {
        try {
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_TTS_ENABLED, true) || !ttsReady) return
            tts?.speak(
                "Peringatan. Suhu baterai ${String.format(Locale.US, "%.0f", tempC)} derajat Celsius. Sebaiknya hentikan pengisian jika suhu terus meningkat.",
                TextToSpeech.QUEUE_ADD,
                null,
                "charge_temperature"
            )
        } catch (_: Exception) {}
    }

    private fun checkTemperature(tempC: Float, pct: Int) {
        if (tempC <= 0f) return
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val enabled = prefs.getBoolean(MainActivity.KEY_TEMP_WARNING, true)
        val limit = prefs.getInt(MainActivity.KEY_TEMP_LIMIT, 45).coerceIn(40, 55)
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (!enabled) {
            tempWarningActive = false
            return
        }

        if (tempC >= limit) {
            val now = System.currentTimeMillis()
            mgr.notify(
                NOTIF_ID_TEMP,
                NotificationCompat.Builder(this, CHANNEL_TEMP)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🌡️ Suhu baterai tinggi")
                    .setContentText(String.format(Locale.US, "Suhu sekarang %.1f°C (batas %d°C)", tempC, limit))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(
                        String.format(Locale.US, "Suhu baterai %.1f°C sudah mencapai batas %d°C. Periksa charger dan pertimbangkan mencabutnya jika suhu terus naik.", tempC, limit)
                    ))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOngoing(true)
                    .build()
            )
            if (!tempWarningActive || now - lastTempSpeech > 120_000L) {
                speakTemperature(tempC)
                lastTempSpeech = now
            }
            tempWarningActive = true
        } else if (tempWarningActive) {
            tempWarningActive = false
            mgr.cancel(NOTIF_ID_TEMP)
        }
    }

    private fun playAlarmSound() {
        try {
            stopMediaPlayerOnly()
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            val volumePercent = prefs.getInt(MainActivity.KEY_VOLUME, 80).coerceIn(0, 100)
            val volume = volumePercent / 100f
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            val customId = resources.getIdentifier("alarm", "raw", packageName)
            if (customId != 0) {
                val afd = resources.openRawResourceFd(customId)
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: throw IllegalStateException("Tidak ada ringtone")
                player.setDataSource(this, uri)
            }

            player.setVolume(volume, volume)
            player.isLooping = true
            player.prepare()
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            Log.e(MainActivity.TAG, "MediaPlayer alarm gagal", e)
            try {
                val volumePercent = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                    .getInt(MainActivity.KEY_VOLUME, 80)
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, volumePercent)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 30_000)
            } catch (_: Exception) {}
        }
    }

    private fun stopMediaPlayerOnly() {
        try { mediaPlayer?.stop() } catch (_: Exception) {}
        try { mediaPlayer?.reset() } catch (_: Exception) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun vibrateAlarm() {
        try {
            val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 600, 400, 600, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopAlarm() {
        stopMediaPlayerOnly()
        try { toneGenerator?.stopTone() } catch (_: Exception) {}
        try { toneGenerator?.release() } catch (_: Exception) {}
        toneGenerator = null
        try { (getSystemService(VIBRATOR_SERVICE) as Vibrator).cancel() } catch (_: Exception) {}
        try { (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID_ALARM) } catch (_: Exception) {}
        handler.removeCallbacks(ttsLoop)
        try { tts?.stop() } catch (_: Exception) {}
    }

    private fun startSession(pct: Int, target: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(MainActivity.KEY_SESSION_START, now)
            .putInt(MainActivity.KEY_SESSION_START_LEVEL, pct)
            .putLong(MainActivity.KEY_SESSION_TARGET_TIME, 0L)
            .putInt(MainActivity.KEY_SESSION_TARGET_LEVEL, target)
            .putBoolean(MainActivity.KEY_SESSION_ACTIVE, true)
            .apply()
    }

    private fun markTargetReached(pct: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(MainActivity.KEY_SESSION_ACTIVE, false) &&
            prefs.getLong(MainActivity.KEY_SESSION_TARGET_TIME, 0L) == 0L) {
            prefs.edit()
                .putLong(MainActivity.KEY_SESSION_TARGET_TIME, System.currentTimeMillis())
                .putInt(MainActivity.KEY_SESSION_TARGET_LEVEL, pct)
                .apply()
        }
    }

    private fun finishSession(endPct: Int) {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean(MainActivity.KEY_SESSION_ACTIVE, false)) return

        val start = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        if (start <= 0L) {
            prefs.edit().putBoolean(MainActivity.KEY_SESSION_ACTIVE, false).apply()
            return
        }

        val now = System.currentTimeMillis()
        val startLevel = prefs.getInt(MainActivity.KEY_SESSION_START_LEVEL, endPct)
        val targetLevel = prefs.getInt(MainActivity.KEY_SESSION_TARGET_LEVEL, prefs.getInt(MainActivity.KEY_LIMIT, 80))
        val targetTime = prefs.getLong(MainActivity.KEY_SESSION_TARGET_TIME, 0L)
        val elapsed = (now - start).coerceAtLeast(0L)
        val speed = if (elapsed > 30_000L && endPct > startLevel) {
            (endPct - startLevel) / (elapsed / 3_600_000.0)
        } else 0.0

        val item = JSONObject().apply {
            put("start", formatDate(start))
            put("end", formatDate(now))
            put("startLevel", startLevel)
            put("endLevel", endPct)
            put("targetLevel", targetLevel)
            put("targetTime", if (targetTime > 0) formatDate(targetTime) else "Belum tercapai")
            put("durationMs", elapsed)
            put("speed", speed)
        }

        val old = try { JSONArray(prefs.getString(MainActivity.KEY_HISTORY, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        val result = JSONArray()
        result.put(item)
        for (i in 0 until minOf(old.length(), 19)) result.put(old.getJSONObject(i))

        prefs.edit()
            .putString(MainActivity.KEY_HISTORY, result.toString())
            .putBoolean(MainActivity.KEY_SESSION_ACTIVE, false)
            .apply()
    }

    private fun updateChargingMetrics(pct: Int, target: Int): Pair<Float, Long> {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val start = prefs.getLong(MainActivity.KEY_SESSION_START, 0L)
        val startLevel = prefs.getInt(MainActivity.KEY_SESSION_START_LEVEL, pct)
        if (start <= 0L) {
            startSession(pct, target)
            return Pair(0f, -1L)
        }

        val elapsed = System.currentTimeMillis() - start
        val gained = pct - startLevel
        if (elapsed < 30_000L || gained <= 0) return Pair(0f, -1L)

        val speed = gained / (elapsed / 3_600_000f)
        val remaining = (target - pct).coerceAtLeast(0)
        val etaMinutes = if (speed > 0.01f) ((remaining / speed) * 60f).toLong() else -1L

        prefs.edit()
            .putFloat(MainActivity.KEY_CHARGE_SPEED, speed)
            .putLong(MainActivity.KEY_ETA_MINUTES, etaMinutes)
            .apply()

        return Pair(speed, etaMinutes)
    }

    private fun updateTelemetry(pct: Int, temp: Float, charging: Boolean, speed: Float, eta: Long) {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putInt(MainActivity.KEY_CURRENT_LEVEL, pct)
            .putFloat(MainActivity.KEY_CURRENT_TEMP, temp)
            .putBoolean(MainActivity.KEY_CURRENT_CHARGING, charging)
            .putFloat(MainActivity.KEY_CHARGE_SPEED, speed)
            .putLong(MainActivity.KEY_ETA_MINUTES, eta)
            .apply()
    }

    private fun updateForegroundNotif(pct: Int, charging: Boolean, limit: Int, temp: Float) {
        try {
            val state = if (charging) "⚡ Charging" else "🔌 Tidak charging"
            val extraTemp = if (temp > 0f) String.format(Locale.US, " • %.1f°C", temp) else ""
            val text = "$state • $pct% / $limit%$extraTemp"
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID_FG, buildForegroundNotification(text, pct, charging, temp))
        } catch (_: Exception) {}
    }

    private fun buildForegroundNotification(text: String, pct: Int, charging: Boolean, temp: Float): Notification {
        val stopPi = actionPendingIntent("com.example.chargealarm.STOP_ALARM", 1)
        return NotificationCompat.Builder(this, CHANNEL_SILENT)
            .setContentTitle("🔋 Charge Alarm")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Matikan", stopPi)
            .build()
    }

    private fun buildAlarmNotification(pct: Int, withSnooze: Boolean): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔔 Baterai $pct% — Cabut Charger!")
            .setContentText("Alarm + Text-to-Speech aktif sampai charger dicabut atau alarm dihentikan.")
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

    private fun actionPendingIntent(action: String, reqCode: Int): PendingIntent {
        val i = Intent(this, AlarmActionReceiver::class.java).apply { this.action = action }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(this, reqCode, i, flags)
    }

    private fun formatDate(time: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID")).format(Date(time))
}

// ============================================================
// ALARM ACTION RECEIVER
// ============================================================
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                "com.example.chargealarm.STOP_ALARM" -> {
                    BatteryMonitorService.start(context, "STOP_ALARM_ONLY")
                }
                "com.example.chargealarm.SNOOZE_ALARM" -> {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val i = Intent(context, SnoozeReceiver::class.java)
                    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    else PendingIntent.FLAG_UPDATE_CURRENT
                    val pi = PendingIntent.getBroadcast(context, 20, i, flags)
                    val triggerAt = System.currentTimeMillis() + 60_000L
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    else am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)

                    context.startService(Intent(context, BatteryMonitorService::class.java).apply { action = "SNOOZE_STOP" })
                }
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "AlarmActionReceiver error", e)
        }
    }
}

// ============================================================
// SNOOZE RECEIVER
// ============================================================
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0

            if (!charging) {
                BatteryMonitorService.stop(context)
                return
            }

            BatteryMonitorService.start(context, "SNOOZE_TRIGGER")
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "SnoozeReceiver error", e)
        }
    }
}

// ============================================================
// POWER CONNECTION RECEIVER
// ============================================================
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)) return

            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    BatteryMonitorService.start(context)
                    prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, true).apply()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
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
// BOOT RECEIVER
// ============================================================
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
            val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            val autoMode = prefs.getBoolean(MainActivity.KEY_AUTO_MODE, true)
            val active = prefs.getBoolean(MainActivity.KEY_ACTIVE, false)
            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

            val charging = if (batteryIntent != null) {
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
            } else false

            if (active || (autoMode && charging)) {
                BatteryMonitorService.start(context)
                prefs.edit().putBoolean(MainActivity.KEY_ACTIVE, true).apply()
            }
        } catch (e: Throwable) {
            Log.e(MainActivity.TAG, "BootReceiver error", e)
        }
    }
}
