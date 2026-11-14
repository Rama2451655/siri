package com.rama.riya

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.animation.ValueAnimator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // ---- colors ----
    private val bg1 = 0xFF0B1220.toInt()
    private val bg2 = 0xFF16304A.toInt()
    private val card = 0xFF1A2A40.toInt()
    private val field = 0xFF0F1B2C.toInt()
    private val accent = 0xFF4DD0E1.toInt()
    private val accent2 = 0xFF3D7BFF.toInt()
    private val textMain = 0xFFEAF6FF.toInt()
    private val muted = 0xFF8FA7BD.toInt()
    private val good = 0xFF4ADE80.toInt()

    private lateinit var prefs: SharedPreferences
    private lateinit var nameBox: EditText
    private lateinit var statusText: TextView
    private lateinit var toggleBtn: TextView
    private lateinit var glow: View
    private lateinit var commandsText: TextView
    private lateinit var heardText: TextView
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "heard" || key == "running") runOnUiThread { refresh() }
    }
    private var pulse: ObjectAnimator? = null
    private val checks = mutableListOf<Pair<TextView, () -> Boolean>>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun lp(top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) }

    private fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun cardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(card, 22)
        setPadding(dp(18), dp(18), dp(18), dp(18))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("riya", Context.MODE_PRIVATE)
        window.statusBarColor = bg1
        window.navigationBarColor = bg2

        val root = ScrollView(this).apply {
            isFillViewport = true
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(bg1, bg2))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(32))
        }
        root.addView(content)

        // ---------- Header orb ----------
        val orbFrame = FrameLayout(this)
        glow = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(65).toFloat()
                colors = intArrayOf(0x884DD0E1.toInt(), 0x004DD0E1)
            }
        }
        val core = TextView(this).apply {
            text = "\uD83C\uDF99"
            textSize = 38f
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accent, accent2)).apply {
                shape = GradientDrawable.OVAL
            }
        }
        orbFrame.addView(glow, FrameLayout.LayoutParams(dp(130), dp(130), Gravity.CENTER))
        orbFrame.addView(core, FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))
        content.addView(orbFrame, LinearLayout.LayoutParams(dp(130), dp(130)).apply { gravity = Gravity.CENTER_HORIZONTAL })

        content.addView(tv("Voice Assistant", 28f, textMain, true).apply { gravity = Gravity.CENTER }, lp(12))
        content.addView(tv("Hands-free control with your own wake name", 14f, muted).apply { gravity = Gravity.CENTER }, lp(4))

        statusText = tv("", 15f, muted, true).apply { gravity = Gravity.CENTER }
        content.addView(statusText, lp(18))

        // ---------- Start / Stop button ----------
        toggleBtn = tv("START", 18f, 0xFF06202B.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            letterSpacing = 0.12f
            setOnClickListener { toggle() }
        }
        content.addView(toggleBtn, lp(14))

        // ---------- Heard card ----------
        val heardCard = cardLayout()
        heardCard.addView(tv("LAST HEARD", 12f, accent, true))
        heardText = tv("\u2014", 16f, textMain)
        heardCard.addView(heardText, lp(6))
        content.addView(heardCard, lp(18))

        // ---------- Name card ----------
        val nameCard = cardLayout()
        nameCard.addView(tv("ASSISTANT NAME", 12f, accent, true))
        nameCard.addView(tv("Say this name before every command", 13f, muted), lp(2))
        nameBox = EditText(this).apply {
            hint = "e.g. Riya, Jarvis, Maya"
            setText(prefs.getString("name", "Riya"))
            setTextColor(textMain)
            setHintTextColor(muted)
            textSize = 20f
            setSingleLine()
            background = rounded(field, 14)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        nameCard.addView(nameBox, lp(10))
        content.addView(nameCard, lp(22))

        // ---------- Setup card ----------
        val setup = cardLayout()
        setup.addView(tv("SETUP  \u2022  DO ONCE", 12f, accent, true))
        setup.addView(tv("Tap each row until it shows a green tick", 13f, muted), lp(2))

        addRow(setup, "Microphone, contacts & calls", "Needed to listen, find people and call",
            { requestAllPermissions() },
            {
                listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)
                    .all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            })
        addRow(setup, "Lock screen", "Device admin, so I can lock your phone",
            {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this@MainActivity, AdminReceiver::class.java))
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Needed so the assistant can lock your screen.")
                }
                startActivity(intent)
            },
            {
                (getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager)
                    .isAdminActive(ComponentName(this, AdminReceiver::class.java))
            })
        addRow(setup, "Display over other apps", "Lets me open camera, YouTube, Spotify",
            { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) },
            { Settings.canDrawOverlays(this) })
        addRow(setup, "Background running", "Choose Allow, so I keep listening",
            { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) },
            { (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName) })
        addRow(setup, "WhatsApp auto-send", "Accessibility \u2192 turn on the assistant",
            { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            {
                Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                    ?.contains(packageName) == true
            })
        addRow(setup, "Pop-ups in background", "Xiaomi/Oppo/Vivo: allow background pop-ups & autostart",
            { openPermEditor() }, null)
        content.addView(setup, lp(16))

        // ---------- Commands card ----------
        val cmds = cardLayout()
        cmds.addView(tv("TRY SAYING", 12f, accent, true))
        commandsText = tv("", 14f, textMain).apply { setLineSpacing(0f, 1.35f) }
        cmds.addView(commandsText, lp(10))
        content.addView(cmds, lp(16))

        setContentView(root)
    }

    private fun addRow(parent: LinearLayout, title: String, desc: String, onClick: () -> Unit, check: (() -> Boolean)?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(field, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { onClick() }
        }
        val mark = tv("\u25CB", 22f, muted, true).apply { gravity = Gravity.CENTER }
        row.addView(mark, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(title, 15f, textMain, true))
        col.addView(tv(desc, 12f, muted))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tv("\u203A", 24f, muted))
        parent.addView(row, lp(10))
        if (check != null) checks.add(mark to check)
        else { mark.text = "!"; mark.setTextColor(0xFFFFB74D.toInt()) }
    }

    private fun openPermEditor() {
        try {
            startActivity(Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                putExtra("extra_pkgname", packageName)
            })
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestAllPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun isRunning() = prefs.getBoolean("running", false)

    private fun toggle() {
        val svc = Intent(this, RiyaService::class.java)
        if (isRunning()) {
            stopService(svc)
            toast("Stopped")
        } else {
            val name = nameBox.text.toString().trim().ifEmpty { "Riya" }
            prefs.edit().putString("name", name).apply()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                toast("Allow Microphone first (first row)")
                requestAllPermissions()
                return
            }
            stopService(svc)
            startForegroundService(svc)
            toast("Started. Say: $name what's the time")
        }
        toggleBtn.postDelayed({ refresh() }, 700)
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        refresh()
    }

    override fun onPause() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun refresh() {
        // setup ticks
        for ((mark, check) in checks) {
            val ok = check()
            mark.text = if (ok) "\u2714" else "\u25CB"
            mark.setTextColor(if (ok) good else muted)
        }
        heardText.text = "\u201C" + prefs.getString("heard", "nothing yet") + "\u201D"
        // running state
        val name = nameBox.text.toString().trim().ifEmpty { "Riya" }
        val running = isRunning()
        if (running) {
            statusText.text = "\u25CF  Listening for \u201C$name\u201D"
            statusText.setTextColor(good)
            toggleBtn.text = "STOP"
            toggleBtn.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFFF8A80.toInt(), 0xFFFF5252.toInt())).apply { cornerRadius = dp(30).toFloat() }
            if (pulse == null) {
                pulse = ObjectAnimator.ofPropertyValuesHolder(
                    glow,
                    PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f),
                    PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f)
                ).apply {
                    duration = 900
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    start()
                }
            }
        } else {
            statusText.text = "\u25CB  Not running"
            statusText.setTextColor(muted)
            toggleBtn.text = "START"
            toggleBtn.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(accent, accent2)).apply { cornerRadius = dp(30).toFloat() }
            pulse?.cancel()
            pulse = null
            glow.scaleX = 1f
            glow.scaleY = 1f
        }
        commandsText.text =
            "\uD83D\uDD12  $name lock screen\n" +
            "\uD83D\uDCF7  $name open camera\n" +
            "\u25B6\uFE0F  $name open YouTube\n" +
            "\uD83C\uDFB5  $name play <song>  /  open Spotify\n" +
            "\uD83D\uDCDE  $name call <name>\n" +
            "\uD83D\uDCAC  $name send WhatsApp to <name> saying <message>\n" +
            "\uD83D\uDD26  $name flashlight on / off\n" +
            "\uD83D\uDD0A  $name volume up / down / 50\n" +
            "\uD83D\uDD52  $name what's the time"
    }
}
