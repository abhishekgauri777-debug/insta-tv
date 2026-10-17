package com.example.firetvinsta

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** Simple circular pointer drawn over the WebView. */
class CursorView(context: Context) : View(context) {
    var cx = 0f
    var cy = 0f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 4f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(cx, cy, 18f, fill)
        canvas.drawCircle(cx, cy, 18f, ring)
    }
}

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var cursor: CursorView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = DESKTOP_UA
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            isFocusable = false // we drive input ourselves
            webViewClient = WebViewClient() // keep navigation inside the app
            webChromeClient = WebChromeClient()
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)

        cursor = CursorView(this)

        val root = FrameLayout(this)
        root.addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(cursor, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        root.post {
            cursor.cx = root.width / 2f
            cursor.cy = root.height / 2f
            cursor.invalidate()
        }

        web.loadUrl("https://www.instagram.com")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        // Speed up the longer a direction key is held
        val step = 25f + minOf(event.repeatCount, 20) * 6f
        val edge = 80f

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (down) move(-step, 0f, edge); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (down) move(step, 0f, edge); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (down) move(0f, -step, edge); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (down) move(0f, step, edge); return true }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) click()
                return true
            }

            // Fast scroll with media keys
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { if (down) web.scrollBy(0, 600); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { if (down) web.scrollBy(0, -600); return true }
            // Play/pause: click the middle of the screen (toggles a playing reel/video)
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (event.action == KeyEvent.ACTION_UP) tap(web.width / 2f, web.height / 2f)
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    if (web.canGoBack()) web.goBack() else finish()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Move the pointer; when it pushes against the top/bottom edge, scroll the page instead. */
    private fun move(dx: Float, dy: Float, edge: Float) {
        val w = cursor.width.toFloat()
        val h = cursor.height.toFloat()
        cursor.cx = (cursor.cx + dx).coerceIn(0f, w)
        val ny = cursor.cy + dy
        when {
            ny < edge && dy < 0 -> { cursor.cy = edge; web.scrollBy(0, dy.toInt() * 2) }
            ny > h - edge && dy > 0 -> { cursor.cy = h - edge; web.scrollBy(0, dy.toInt() * 2) }
            else -> cursor.cy = ny.coerceIn(0f, h)
        }
        cursor.invalidate()
    }

    private fun click() = tap(cursor.cx, cursor.cy)

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0)
        web.dispatchTouchEvent(down)
        web.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
}
