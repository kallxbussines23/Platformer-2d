package id.kayn.platformer

import android.app.Activity
import android.os.Bundle
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = 5894 or 1024 or 512
        setContentView(GameView(this))
    }
}

private data class Platform(val x: Float, val height: Float, val width: Float, val kind: String)
private class GameView(context: android.content.Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var px = 90f; private var py = 0f
    private var vx = 0f; private var vy = 0f
    private var camera = 0f; private var floor = 0f
    private var grounded = false; private var onIce = false
    private var left = false; private var right = false; private var jumpQueued = false
    private var coins = 0; private var lives = 3; private var gameOver = false; private var won = false
    private var time = 0f; private var previous = System.nanoTime()
    private val collected = mutableSetOf<Int>()
    private val brokenAt = mutableMapOf<Int, Float>()
    private val coinXs = listOf(330f, 620f, 920f, 1240f, 1540f, 1880f, 2210f)
    private val enemies = listOf(540f, 1120f, 1700f, 2050f)
    private val ground = listOf(0f to 420f, 475f to 760f, 815f to 1080f, 1160f to 1435f, 1500f to 1790f, 1850f to 2160f, 2220f to 2600f)
    private val platforms = listOf(
        Platform(275f, 115f, 125f, "grass"), Platform(680f, 165f, 135f, "wood"),
        Platform(980f, 125f, 150f, "ice"), Platform(1370f, 180f, 145f, "fragile"),
        Platform(1640f, 125f, 155f, "stone"), Platform(1980f, 180f, 150f, "ice"),
        Platform(2200f, 100f, 130f, "fragile")
    )

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = min(0.032f, (now - previous) / 1_000_000_000f).coerceAtLeast(0f)
        previous = now; time += dt
        val w = width.toFloat(); val h = height.toFloat()
        floor = h * 0.80f
        if (py == 0f) py = floor - 54f
        drawBackground(c, w, h)
        if (!gameOver && !won) updatePhysics(dt, h)
        camera = max(0f, px - w * 0.31f)
        drawLevel(c, w, h)
        drawCoins(c)
        drawEnemies(c)
        drawPlayer(c)
        drawHud(c, w, h)
        if (gameOver || won) drawEndScreen(c, w, h)
        postInvalidateOnAnimation()
    }

    private fun updatePhysics(dt: Float, h: Float) {
        val wasGrounded = grounded
        if (left) vx -= (if (onIce) 650f else 1800f) * dt
        if (right) vx += (if (onIce) 650f else 1800f) * dt
        if (!left && !right) vx *= if (onIce) 0.985f else 0.78f
        vx = vx.coerceIn(-280f, 280f)
        if (jumpQueued && grounded) { vy = -590f; grounded = false; onIce = false }
        jumpQueued = false
        val oldBottom = py + 54f
        val oldX = px
        px += vx * dt
        vy += 1450f * dt
        py += vy * dt
        grounded = false; onIce = false
        val newBottom = py + 54f
        for ((start, end) in ground) {
            if (px + 17f > start && px - 17f < end && newBottom >= floor && oldBottom <= floor + 18f && vy >= 0f) {
                py = floor - 54f; vy = 0f; grounded = true
            }
        }
        platforms.forEachIndexed { i, pl ->
            if (pl.kind == "fragile" && brokenAt.containsKey(i)) return@forEachIndexed
            val top = floor - pl.height
            val overlaps = px + 17f > pl.x && px - 17f < pl.x + pl.width
            if (overlaps && vy >= 0f && oldBottom <= top + 10f && newBottom >= top) {
                py = top - 54f; vy = 0f; grounded = true
                if (pl.kind == "ice") onIce = true
                if (pl.kind == "fragile") brokenAt.putIfAbsent(i, time)
            }
        }
        brokenAt.entries.toList().forEach { (i, at) -> if (at >= 0f && time - at > 0.95f) brokenAt[i] = -1f }
        // A negative timestamp marks a platform that has crumbled permanently.
        if (px < 0f) { px = 0f; vx = 0f }
        if (py > h + 100f) { loseLife() }
        if (px > 2490f) won = true
        // A fresh landing cancels accumulated airborne input for predictable controls.
        if (!wasGrounded && grounded && abs(vx) < 8f) vx = 0f
    }

    private fun loseLife() {
        lives--; px = max(0f, px - 150f); py = floor - 54f; vx = 0f; vy = 0f
        if (lives <= 0) gameOver = true
    }

    private fun drawBackground(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.rgb(116, 194, 235))
        // Cloud bands and distant mountains move at different rates.
        p.color = Color.rgb(196, 230, 247)
        for (i in -1..7) {
            val x = i * 245f - camera * .12f % 245f
            c.drawCircle(x + 60f, h * .22f, 28f, p); c.drawCircle(x + 91f, h * .20f, 39f, p); c.drawCircle(x + 128f, h * .23f, 25f, p)
        }
        p.color = Color.rgb(109, 169, 207)
        for (i in -1..6) {
            val x = i * 300f - camera * .22f % 300f
            val path = android.graphics.Path().apply { moveTo(x, floor); lineTo(x + 120f, h * .25f); lineTo(x + 245f, floor); close() }
            c.drawPath(path, p)
        }
        p.color = Color.rgb(67, 144, 126)
        for (i in -1..8) {
            val x = i * 250f - camera * .43f % 250f
            c.drawCircle(x + 80f, floor - 15f, 90f, p); c.drawCircle(x + 185f, floor - 8f, 105f, p)
        }
    }

    private fun drawLevel(c: Canvas, w: Float, h: Float) {
        ground.forEach { (start, end) ->
            val x = start - camera; val r = end - camera
            if (r < -10f || x > w + 10f) return@forEach
            p.color = Color.rgb(104, 66, 45); c.drawRoundRect(x, floor, r, floor + 100f, 7f, 7f, p)
            p.color = Color.rgb(139, 88, 53)
            var bx = x
            while (bx < r) {
                c.drawRoundRect(bx + 3f, floor + 13f, min(bx + 32f, r - 2f), floor + 34f, 5f, 5f, p)
                c.drawRoundRect(bx + 13f, floor + 44f, min(bx + 44f, r - 2f), floor + 66f, 5f, 5f, p)
                p.color = Color.rgb(76, 48, 37); c.drawLine(bx + 8f, floor + 35f, bx + 12f, floor + 40f, p); p.color = Color.rgb(139, 88, 53)
                bx += 44f
            }
            p.color = Color.rgb(70, 174, 67); c.drawRoundRect(x, floor - 9f, r, floor + 8f, 7f, 7f, p)
            p.color = Color.rgb(170, 225, 91)
            var gx = x + 4f
            while (gx < r) { c.drawRoundRect(gx, floor - 10f, gx + 11f, floor - 3f, 4f, 4f, p); gx += 22f }
        }
        platforms.forEachIndexed { i, pl ->
            val x = pl.x - camera; val top = floor - pl.height
            if (x + pl.width < -20f || x > w + 20f) return@forEachIndexed
            if (pl.kind == "fragile" && brokenAt.containsKey(i)) {
                val elapsed = time - brokenAt[i]!!
                if (elapsed > .95f || brokenAt[i]!! < 0f) return@forEachIndexed
                c.save(); c.rotate(kotlin.math.sin(elapsed * 36f) * 3f, x + pl.width / 2f, top + 12f)
            }
            when (pl.kind) {
                "ice" -> drawIce(c, x, top, pl.width)
                "wood", "fragile" -> drawWood(c, x, top, pl.width, pl.kind == "fragile", i)
                "stone" -> drawStone(c, x, top, pl.width)
                else -> drawGrassPlatform(c, x, top, pl.width)
            }
            if (pl.kind == "fragile" && brokenAt.containsKey(i)) {
                val elapsed = time - brokenAt[i]!!
                p.color = Color.rgb(71, 43, 31)
                for (j in 0..3) c.drawLine(x + 24f + j * 26f, top + 4f, x + 18f + j * 26f, top + 17f + elapsed * 4f, p)
                c.restore()
            }
        }
        // Simple spikes on a dangerous gap.
        val sx = 1088f - camera
        p.color = Color.rgb(208, 215, 224)
        for (i in 0..4) {
            val x = sx + i * 23f
            c.drawPath(android.graphics.Path().apply { moveTo(x, floor + 1f); lineTo(x + 11f, floor - 25f); lineTo(x + 22f, floor + 1f); close() }, p)
            p.color = Color.rgb(100, 111, 132); c.drawLine(x + 11f, floor - 22f, x + 15f, floor - 8f, p); p.color = Color.rgb(208, 215, 224)
        }
    }

    private fun drawGrassPlatform(c: Canvas, x: Float, y: Float, width: Float) {
        p.color = Color.rgb(112, 72, 46); c.drawRoundRect(x, y, x + width, y + 27f, 5f, 5f, p)
        p.color = Color.rgb(79, 177, 66); c.drawRoundRect(x, y - 8f, x + width, y + 5f, 5f, 5f, p)
        p.color = Color.rgb(177, 229, 95)
        for (i in 0 until (width / 22f).toInt()) c.drawRoundRect(x + i * 22f + 3f, y - 8f, x + i * 22f + 14f, y - 3f, 3f, 3f, p)
        p.color = Color.rgb(153, 103, 62)
        for (i in 0 until (width / 36f).toInt()) c.drawRoundRect(x + i * 36f + 4f, y + 10f, x + i * 36f + 23f, y + 20f, 3f, 3f, p)
    }

    private fun drawWood(c: Canvas, x: Float, y: Float, width: Float, fragile: Boolean, index: Int) {
        p.color = Color.rgb(105, 60, 34); c.drawRoundRect(x, y, x + width, y + 24f, 4f, 4f, p)
        p.color = Color.rgb(180, 113, 58); c.drawRoundRect(x + 2f, y + 2f, x + width - 2f, y + 10f, 3f, 3f, p)
        p.color = Color.rgb(145, 82, 43); c.drawRoundRect(x + 2f, y + 12f, x + width - 2f, y + 21f, 3f, 3f, p)
        p.color = Color.rgb(226, 162, 89)
        for (i in 0 until (width / 40f).toInt()) {
            val bx = x + 9f + i * 40f
            c.drawLine(bx, y + 5f, bx + 22f, y + 5f, p); c.drawCircle(bx + 25f, y + 16f, 1.8f, p)
        }
        p.color = Color.rgb(70, 43, 30); c.drawCircle(x + 9f, y + 6f, 2.5f, p); c.drawCircle(x + width - 9f, y + 6f, 2.5f, p)
        if (fragile) {
            p.color = Color.rgb(80, 43, 29)
            c.drawLine(x + width * .43f, y + 2f, x + width * .47f, y + 10f, p)
            c.drawLine(x + width * .47f, y + 10f, x + width * .44f, y + 16f, p)
            c.drawLine(x + width * .44f, y + 16f, x + width * .51f, y + 22f, p)
            if (brokenAt.containsKey(index)) {
                val progress = ((time - brokenAt[index]!!) / .95f).coerceIn(0f, 1f)
                p.color = Color.argb((progress * 180).toInt(), 40, 20, 15)
                c.drawRoundRect(x + 2f, y + 2f, x + width - 2f, y + 22f, 3f, 3f, p)
            }
        }
    }

    private fun drawIce(c: Canvas, x: Float, y: Float, width: Float) {
        p.color = Color.rgb(81, 154, 197); c.drawRoundRect(x, y, x + width, y + 28f, 5f, 5f, p)
        p.color = Color.rgb(136, 218, 247); c.drawRoundRect(x, y - 7f, x + width, y + 8f, 5f, 5f, p)
        p.color = Color.rgb(218, 248, 255); c.drawLine(x + 8f, y - 3f, x + width * .42f, y - 3f, p)
        c.drawLine(x + width * .60f, y + 2f, x + width - 9f, y + 2f, p)
        p.color = Color.rgb(71, 153, 207)
        c.drawLine(x + width * .28f, y + 10f, x + width * .34f, y + 17f, p)
        c.drawLine(x + width * .34f, y + 17f, x + width * .30f, y + 23f, p)
        c.drawLine(x + width * .76f, y + 10f, x + width * .71f, y + 17f, p)
        // tiny frost sparkles animate across the slick surface
        p.color = Color.WHITE
        val shine = x + ((time * 45f) % max(1f, width))
        c.drawCircle(shine, y - 4f, 2.5f, p)
    }

    private fun drawStone(c: Canvas, x: Float, y: Float, width: Float) {
        p.color = Color.rgb(80, 91, 122); c.drawRoundRect(x, y, x + width, y + 28f, 4f, 4f, p)
        p.color = Color.rgb(161, 176, 208); c.drawRoundRect(x, y - 6f, x + width, y + 7f, 4f, 4f, p)
        p.color = Color.rgb(107, 119, 151)
        for (i in 0 until (width / 35f).toInt()) c.drawRoundRect(x + i * 35f + 3f, y + 10f, x + i * 35f + 30f, y + 24f, 3f, 3f, p)
        p.color = Color.rgb(207, 222, 244); c.drawLine(x + 9f, y - 2f, x + 24f, y - 2f, p)
    }

    private fun drawCoins(c: Canvas) {
        coinXs.forEachIndexed { i, worldX ->
            if (i in collected) return@forEachIndexed
            val x = worldX - camera; val y = floor - 74f - if (i % 2 == 0) 28f else 0f
            val squash = .28f + abs(kotlin.math.sin(time * 3.5f + i)) * .72f
            p.color = Color.rgb(199, 124, 17); c.drawOval(x - 12f * squash, y - 15f, x + 12f * squash, y + 15f, p)
            p.color = Color.rgb(255, 202, 49); c.drawOval(x - 9f * squash, y - 12f, x + 9f * squash, y + 12f, p)
            p.color = Color.rgb(255, 238, 139); c.drawCircle(x - 3f, y - 5f, 2.5f, p)
            if (abs(px - worldX) < 28f && abs(py + 27f - y) < 45f) coinsCollected(i)
        }
    }
    private fun coinsCollected(i: Int) { if (collected.add(i)) coins++ }

    private fun drawEnemies(c: Canvas) {
        enemies.forEachIndexed { i, ex ->
            val x = ex - camera
            if (x < -50f || x > width + 50f) return@forEachIndexed
            val bob = abs(kotlin.math.sin(time * 4.2f + i)) * 5f
            val squish = 1f + kotlin.math.sin(time * 5f + i) * .06f
            p.color = Color.rgb(58, 130, 45)
            c.drawOval(x - 23f * squish, floor - 43f + bob, x + 23f * squish, floor - 4f - bob * .25f, p)
            p.color = Color.rgb(103, 213, 62)
            c.drawOval(x - 18f * squish, floor - 40f + bob, x + 17f * squish, floor - 12f - bob * .25f, p)
            p.color = Color.WHITE; c.drawCircle(x - 7f, floor - 29f + bob, 5f, p); c.drawCircle(x + 8f, floor - 29f + bob, 5f, p)
            p.color = Color.rgb(28, 40, 27); c.drawCircle(x - 6f, floor - 28f + bob, 2.4f, p); c.drawCircle(x + 9f, floor - 28f + bob, 2.4f, p)
            if (abs(px - ex) < 36f && py + 50f > floor - 43f && py < floor) {
                if (vy > 90f) { vy = -350f } else { lives--; px = max(0f, px - 135f); vx = -vx * .5f; if (lives <= 0) gameOver = true }
            }
        }
    }

    private fun drawPlayer(c: Canvas) {
        val x = px - camera
        val running = abs(vx) > 25f && grounded
        val bob = if (grounded) kotlin.math.sin(time * if (running) 15f else 3f) * if (running) 3f else 1.5f else 0f
        val leg = if (running) kotlin.math.sin(time * 15f) * 7f else 0f
        val y = py + bob
        // Shadow
        p.color = Color.argb(55, 22, 34, 43); c.drawOval(x - 22f, floor - 5f, x + 22f, floor + 2f, p)
        // Shoes and legs animate independently
        p.color = Color.rgb(31, 33, 45)
        c.drawRoundRect(x - 17f, y + 42f, x - 2f, y + 53f + leg.coerceAtLeast(0f), 4f, 4f, p)
        c.drawRoundRect(x + 2f, y + 42f, x + 17f, y + 53f + (-leg).coerceAtLeast(0f), 4f, 4f, p)
        p.color = Color.rgb(245, 240, 229)
        c.drawRoundRect(x - 20f, y + 49f, x - 2f, y + 55f, 3f, 3f, p); c.drawRoundRect(x + 1f, y + 49f, x + 21f, y + 55f, 3f, 3f, p)
        // Hoodie torso and hood
        p.color = Color.rgb(41, 43, 60); c.drawRoundRect(x - 18f, y + 15f, x + 18f, y + 45f, 10f, 10f, p)
        p.color = Color.rgb(81, 80, 105); c.drawRoundRect(x - 15f, y + 18f, x + 15f, y + 41f, 7f, 7f, p)
        p.color = Color.rgb(244, 237, 223); c.drawRoundRect(x - 7f, y + 22f, x + 7f, y + 40f, 3f, 3f, p)
        // Arms swing during running
        p.color = Color.rgb(241, 188, 145)
        c.drawRoundRect(x - 24f, y + 22f + leg * .25f, x - 15f, y + 37f + leg * .25f, 5f, 5f, p)
        c.drawRoundRect(x + 15f, y + 22f - leg * .25f, x + 24f, y + 37f - leg * .25f, 5f, 5f, p)
        // Face, hair fringe and eyes
        p.color = Color.rgb(244, 194, 153); c.drawOval(x - 16f, y - 13f, x + 16f, y + 20f, p)
        p.color = Color.rgb(55, 38, 37)
        c.drawOval(x - 19f, y - 18f, x + 15f, y + 2f, p)
        c.drawCircle(x - 11f, y - 6f, 7f, p); c.drawCircle(x + 2f, y - 12f, 8f, p)
        p.color = Color.rgb(255, 255, 255); c.drawOval(x - 8f, y + 1f, x - 1f, y + 10f, p)
        p.color = Color.rgb(31, 26, 27); c.drawCircle(x - 4f, y + 6f, 2.6f, p)
        p.color = Color.rgb(211, 121, 103); c.drawLine(x + 3f, y + 15f, x + 8f, y + 15f, p)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(220, 25, 37, 54); c.drawRoundRect(16f, 14f, 220f, 60f, 16f, 16f, p)
        p.color = Color.WHITE; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 21f
        c.drawText("♥ $lives     ◉ $coins", 31f, 44f, p)
        if (onIce) {
            p.color = Color.argb(210, 33, 110, 156); c.drawRoundRect(w * .40f, 16f, w * .60f, 48f, 14f, 14f, p)
            p.color = Color.WHITE; p.textSize = 15f; c.drawText("SLIPPERY!", w * .40f + 13f, 37f, p)
        }
        drawButton(c, 24f, h - 88f, 100f, h - 18f, "◀")
        drawButton(c, 108f, h - 88f, 184f, h - 18f, "▶")
        drawButton(c, w - 126f, h - 100f, w - 22f, h - 18f, "JUMP")
    }

    private fun drawButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, text: String) {
        p.color = Color.argb(175, 26, 37, 52); c.drawRoundRect(l, t, r, b, 18f, 18f, p)
        p.color = Color.rgb(214, 226, 236); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
        c.drawRoundRect(l, t, r, b, 18f, 18f, p); p.style = Paint.Style.FILL
        p.color = Color.WHITE; p.textSize = if (text == "JUMP") 16f else 29f; p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(text, (l + r) / 2f - p.measureText(text) / 2f, (t + b) / 2f + 10f, p)
    }

    private fun drawEndScreen(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(220, 13, 23, 36); c.drawRect(0f, 0f, w, h, p)
        p.color = Color.WHITE; p.textSize = 39f; p.typeface = Typeface.DEFAULT_BOLD
        val title = if (won) "LEVEL CLEAR!" else "GAME OVER"
        c.drawText(title, w / 2f - p.measureText(title) / 2f, h / 2f - 5f, p)
        p.textSize = 19f; val sub = "Tap untuk main lagi"
        c.drawText(sub, w / 2f - p.measureText(sub) / 2f, h / 2f + 32f, p)
    }

    private fun restart() {
        px = 90f; py = floor - 54f; vx = 0f; vy = 0f; camera = 0f
        coins = 0; lives = 3; collected.clear(); brokenAt.clear()
        gameOver = false; won = false; grounded = false; onIce = false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat(); val h = height.toFloat()
        if ((gameOver || won) && e.action == MotionEvent.ACTION_DOWN) { restart(); return true }
        when (e.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                left = e.x < 104f && e.y > h - 112f
                right = e.x in 104f..205f && e.y > h - 112f
                if (e.x > w - 145f && e.y > h - 125f) jumpQueued = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { left = false; right = false }
        }
        return true
    }
}