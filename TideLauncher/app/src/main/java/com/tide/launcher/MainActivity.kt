package com.tide.launcher

import android.app.Activity
import android.app.ActivityManager
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private class AppItem(val label: String, val pkg: String, val intent: Intent, val icon: Drawable)

    private var apps: List<AppItem> = emptyList()
    private val cyan = 0xFF33B5E5.toInt()

    // Apps shown in the dock if installed (first 4 found)
    private val pinned = listOf(
        "com.google.android.youtube.tv", "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom", "in.startv.hotstar", "in.startv.hotstar.dplus.tv",
        "com.spotify.tv.android", "com.android.vending",
        "com.android.tv.settings", "com.android.settings"
    )

    private lateinit var home: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var grid: GridView
    private lateinit var ramText: TextView
    private val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View =
            tile(apps[p].label, apps[p].icon, 72)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF16475C.toInt(), 0xFF0A1C28.toInt(), 0xFF050C12.toInt())
        )
        buildHome()
        buildDrawer()
        root.addView(home, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(drawer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        drawer.visibility = View.GONE
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        showHome()
    }

    // ---------- UI ----------

    private fun focusBg(): Drawable {
        val on = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(0x4D33B5E5)
            setStroke(dp(2), cyan)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), on)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun tile(label: String, icon: Drawable, iconDp: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(8))
            addView(ImageView(context).apply { setImageDrawable(icon) },
                LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)))
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            }, LinearLayout.LayoutParams(dp(iconDp + 40), ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun buildHome() {
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        home.addView(TextClock(this).apply {
            format12Hour = "h:mm"; format24Hour = "H:mm"
            textSize = 96f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        home.addView(TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
            textSize = 22f
            setTextColor(0xFF9AA7AD.toInt())
        })
        home.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        ramText = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF9AA7AD.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        home.addView(ramText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dock = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        home.addView(dock, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun buildDrawer() {
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xB8000000.toInt())
            setPadding(dp(48), dp(24), dp(48), dp(16))
        }
        drawer.addView(TextView(this).apply {
            text = "Apps"; textSize = 22f; setTextColor(cyan)
        })
        drawer.addView(View(this).apply { setBackgroundColor(cyan) },
            LinearLayout.LayoutParams(dp(70), dp(3)).apply { bottomMargin = dp(16); topMargin = dp(6) })
        grid = GridView(this).apply {
            numColumns = GridView.AUTO_FIT
            columnWidth = dp(150)
            verticalSpacing = dp(8)
            horizontalSpacing = dp(8)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(0x4D33B5E5); setStroke(dp(2), cyan)
            }
            setDrawSelectorOnTop(false)
            adapter = this@MainActivity.adapter
            setOnItemClickListener { _, _, pos, _ -> launch(apps[pos]) }
        }
        drawer.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun dotsIcon(): Drawable {
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (i in 0..2) for (j in 0..2) c.drawCircle(18f + 30f * i, 18f + 30f * j, 8f, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun dockTile(label: String, icon: Drawable, onClick: () -> Unit): View =
        tile(label, icon, 72).apply {
            isFocusable = true
            isClickable = true
            background = focusBg()
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(14); marginEnd = dp(14) }
        }

    private fun refresh() {
        apps = loadApps()
        adapter.notifyDataSetChanged()

        val picks = ArrayList<AppItem>()
        for (p in pinned) apps.firstOrNull { it.pkg == p }?.let { if (picks.size < 4) picks.add(it) }
        for (a in apps) if (picks.size < 4 && a !in picks) picks.add(a)

        dock.removeAllViews()
        val mid = picks.size / 2
        picks.forEachIndexed { i, a ->
            if (i == mid) dock.addView(dockTile("All apps", dotsIcon()) { showDrawer() })
            dock.addView(dockTile(a.label, a.icon) { launch(a) })
        }
        if (picks.isEmpty()) dock.addView(dockTile("All apps", dotsIcon()) { showDrawer() })
        updateRam()
        if (home.visibility == View.VISIBLE) dock.getChildAt(mid.coerceAtMost(dock.childCount - 1))?.requestFocus()
    }

    // ---------- Logic ----------

    private fun loadApps(): List<AppItem> {
        val pm = packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(q, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                val li = Intent(Intent.ACTION_MAIN).addCategory(cat)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                out.add(AppItem(ri.loadLabel(pm).toString(), pkg, li, ri.loadIcon(pm))) // original app icon
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    private fun launch(a: AppItem) = startSafe(a.intent)

    private fun startSafe(i: Intent) {
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show() }
    }

    private fun showDrawer() {
        home.visibility = View.GONE
        drawer.visibility = View.VISIBLE
        grid.requestFocus()
        grid.setSelection(0)
    }

    private fun showHome() {
        drawer.visibility = View.GONE
        home.visibility = View.VISIBLE
        if (dock.childCount > 0) dock.getChildAt(dock.childCount / 2).requestFocus()
    }

    private fun updateRam() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        ramText.text = "Free memory: ${mi.availMem shr 20} MB"
    }

    private fun freeMemory() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        apps.forEach { try { am.killBackgroundProcesses(it.pkg) } catch (_: Exception) {} }
        Toast.makeText(this, "Memory freed", Toast.LENGTH_SHORT).show()
        ramText.postDelayed({ updateRam() }, 800)
    }

    // Classic options panel, opened with the remote's Menu key
    private fun showMenu() {
        val d = Dialog(this)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val opts: List<Pair<String, () -> Unit>> = listOf(
            "Settings" to { startSafe(Intent(Settings.ACTION_SETTINGS)) },
            "Wi-Fi" to { startSafe(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            "Manage apps" to { startSafe(Intent(Settings.ACTION_APPLICATION_SETTINGS)) },
            "Free memory" to { freeMemory() },
            "Default home" to { startSafe(Intent(Settings.ACTION_HOME_SETTINGS)) },
            "Refresh apps" to { refresh() }
        )
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1D1D1D.toInt())
            setPadding(dp(24), dp(16), dp(24), dp(20))
        }
        var first: View? = null
        opts.chunked(3).forEach { rowOpts ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowOpts.forEach { (label, action) ->
                val b = TextView(this).apply {
                    text = label; textSize = 18f; setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(22), dp(8), dp(22))
                    isFocusable = true; isClickable = true
                    background = focusBg()
                    setOnClickListener { d.dismiss(); action() }
                }
                if (first == null) first = b
                row.addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            col.addView(row)
        }
        d.setContentView(col)
        d.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            setBackgroundDrawable(ColorDrawable(0xFF1D1D1D.toInt()))
        }
        d.setOnKeyListener { dd, k, e ->
            if ((k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_SETTINGS) && e.action == KeyEvent.ACTION_UP) {
                dd.dismiss(); true
            } else false
        }
        d.show()
        first?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showMenu(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Launcher: Back goes drawer -> home, never exits")
    override fun onBackPressed() {
        if (drawer.visibility == View.VISIBLE) showHome()
    }
}
