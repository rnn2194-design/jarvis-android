package com.example.jarvis

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {

    private lateinit var output: LinearLayout
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private val cyan = Color.rgb(76, 218, 244)
    private val bg = Color.rgb(8, 14, 27)
    private val panel = Color.rgb(17, 29, 48)
    private val white = Color.rgb(235, 245, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        tts = TextToSpeech(this, this)
        buildUi()

        addMessage(
            "JARVIS",
            "Hello! I'm JARVIS. Your background service controls are ready. Always-on wake-word detection is not connected yet."
        )
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 18)
            setBackgroundColor(bg)
        }

        val title = TextView(this).apply {
            text = "✦  J A R V I S"
            textSize = 25f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            letterSpacing = .12f
        }
        page.addView(title, LinearLayout.LayoutParams(-1, 64))

        val subtitle = TextView(this).apply {
            text = "PERSONAL ANDROID ASSISTANT"
            textSize = 11f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        page.addView(subtitle, LinearLayout.LayoutParams(-1, 28))

        val scroll = ScrollView(this)
        output = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 14, 4, 14)
        }
        scroll.addView(output)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        input = EditText(this).apply {
            hint = "Ask JARVIS or type a command..."
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 16f
            setSingleLine(true)
            setPadding(14, 10, 14, 10)
            setBackgroundColor(panel)
        }
        page.addView(input, LinearLayout.LayoutParams(-1, 54))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 0)
        }

        val mic = makeButton("🎙 SPEAK")
        mic.setOnClickListener { startSpeech() }

        val send = makeButton("SEND")
        send.setOnClickListener {
            runCommand(input.text.toString())
            input.setText("")
        }

        row.addView(mic, LinearLayout.LayoutParams(0, 54, 1f).apply {
            rightMargin = 8
        })
        row.addView(send, LinearLayout.LayoutParams(0, 54, 1f).apply {
            leftMargin = 8
        })
        page.addView(row)

        val serviceTitle = TextView(this).apply {
            text = "BACKGROUND SERVICE"
            textSize = 13f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 8)
        }
        page.addView(serviceTitle)

        val serviceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val start = makeButton("START SERVICE")
        start.textSize = 12f
        start.setOnClickListener { requestAndStartService() }

        val stop = makeButton("STOP SERVICE")
        stop.textSize = 12f
        stop.setOnClickListener {
            stopService(Intent(this, JarvisListeningService::class.java))
            say("Background service stopped.")
        }

        serviceRow.addView(start, LinearLayout.LayoutParams(0, 50, 1f).apply {
            rightMargin = 5
        })
        serviceRow.addView(stop, LinearLayout.LayoutParams(0, 50, 1f).apply {
            leftMargin = 5
        })
        page.addView(serviceRow)

        val quick = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 14, 0, 0)
        }

        listOf(
            "Open YouTube",
            "Open WhatsApp",
            "Open Maps",
            "What time is it?"
        ).chunked(2).forEach { pair ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            pair.forEach { command ->
                val button = makeButton(command)
                button.textSize = 12f
                button.setOnClickListener { runCommand(command) }
                line.addView(button, LinearLayout.LayoutParams(0, 48, 1f).apply {
                    setMargins(3, 3, 3, 3)
                })
            }
            quick.addView(line)
        }

        page.addView(quick)

        val foot = TextView(this).apply {
            text = "Background service foundation • Wake-word detection is a later upgrade"
            textSize = 10f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 0)
        }
        page.addView(foot)

        setContentView(page)
    }

    private fun requestAndStartService() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), 41)
            return
        }

        startJarvisService()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 41) {
            val audioGranted =
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED

            if (audioGranted) {
                addMessage(
                    "JARVIS",
                    "Microphone permission granted. Tap START SERVICE again to start the service."
                )
            } else {
                say("Microphone permission is required to start this service.")
            }
        }
    }

    private fun startJarvisService() {
        try {
            val serviceIntent = Intent(this, JarvisListeningService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            say("JARVIS background service started. Wake-word detection is not active yet.")
        } catch (e: Exception) {
            say("I couldn't start the background service. Please check permissions.")
        }
    }

    private fun makeButton(label: String) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(white)
        setBackgroundColor(Color.rgb(21, 66, 88))
    }

    private fun addMessage(who: String, message: String) {
        val tv = TextView(this).apply {
            text = "$who\n$message"
            textSize = 14f
            setTextColor(white)
            setPadding(15, 12, 15, 12)
            setBackgroundColor(
                if (who == "YOU") Color.rgb(20, 59, 80) else panel
            )
        }

        val params = LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(0, 5, 0, 9)
        }
        output.addView(tv, params)
    }

    private fun say(message: String) {
        addMessage("JARVIS", message)
        if (ttsReady) {
            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
        }
    }

    private fun startSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            say("Speech recognition is unavailable. Please type your command.")
            return
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }

        try {
            startActivityForResult(intent, 42)
        } catch (_: Exception) {
            say("I couldn't start voice recognition.")
        }
    }

    @Deprecated("Legacy activity result used for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 42 && resultCode == RESULT_OK) {
            val spoken = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()

            if (!spoken.isNullOrBlank()) runCommand(spoken)
        }
    }

    private fun runCommand(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return

        addMessage("YOU", q)
        val s = q.lowercase(Locale.getDefault())

        when {
            s.contains("time") ->
                say("It is " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))

            s.contains("date") || s.contains("today") ->
                say("Today is " + SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()))

            s.contains("open youtube") ->
                launch("com.google.android.youtube", "https://www.youtube.com/")

            s.contains("open whatsapp") ->
                launch("com.whatsapp", "https://wa.me/")

            s.contains("open maps") ->
                launch("com.google.android.apps.maps", "https://maps.google.com/")

            s.contains("open chrome") || s.contains("open browser") ->
                launch("com.android.chrome", "https://www.google.com/")

            s.startsWith("search ") -> {
                val term = q.substringAfter("search ")
                say("Searching the web for $term")
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(
                            "https://www.google.com/search?q=" +
                                android.net.Uri.encode(term)
                        )
                    )
                )
            }

            s.startsWith("call ") -> {
                val number = q.substringAfter("call ").trim()
                say("Opening the dialler for $number")
                startActivity(
                    Intent(
                        Intent.ACTION_DIAL,
                        android.net.Uri.parse("tel:" + android.net.Uri.encode(number))
                    )
                )
            }

            s == "help" || s.contains("what can you do") ->
                say("I can tell the time and date, open apps, search the web and open the dialler. Always-on wake-word detection and online AI are not connected yet.")

            else ->
                say("I heard you say: $q. My AI brain is not connected yet. Try Help.")
        }
    }

    private fun launch(packageName: String, fallback: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

        say(
            if (launchIntent != null) "Opening the app."
            else "The app wasn't found. Opening its web version."
        )

        try {
            startActivity(
                launchIntent ?: Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(fallback)
                )
            )
        } catch (_: Exception) {
            say("I couldn't open that app on this device.")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            ttsReady = true
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}
