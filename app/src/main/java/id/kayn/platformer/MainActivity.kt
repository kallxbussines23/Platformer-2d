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
    private var levelSelect = true
    private var selectedLevel = 1
    private var jumpHeld = false
    private val activePointers = mutableMapOf<Int, Pair<Float, Float>>()
    private var time = 0f; private var previous = System.nanoTime()
    private val collected = mutableSetOf<Int>()
    private val brokenAt = mutableMapOf<Int, Float>()
    private val coinXs: List<Float> get() = listOf(320f, 620f, 920f, 1240f, 1540f, 1880f, 2210f).map { it + (selectedLevel-1)*5f }
    private val enemies: List<Float> get() = when(selectedLevel) {
        1 -> listOf(540f,1120f,1700f,2050f)
        2 -> listOf(500f,900f,1300f,1740f,2140f)
        3 -> listOf(470f,800f,1200f,1580f,1990f,2320f)
        4 -> listOf(520f,920f,1390f,1780f,2200f)
        else -> listOf(480f,790f,1100f,1450f,1770f,2110f,2390f)
    }
    private val ground: List<Pair<Float,Float>> get() = when(selectedLevel) {
        1 -> listOf(0f to 420f,475f to 760f,815f to 1080f,1160f to 1435f,1500f to 1790f,1850f to 2160f,2220f to 2600f)
        2 -> listOf(0f to 360f,420f to 680f,750f to 1000f,1070f to 1320f,1390f to 1660f,1730f to 1990f,2060f to 2600f)
        3 -> listOf(0f to 390f,455f to 650f,720f to 930f,1000f to 1210f,1280f to 1500f,1570f to 1790f,1860f to 2090f,2160f to 2600f)
        4 -> listOf(0f to 350f,420f to 630f,700f to 900f,970f to 1170f,1240f to 1450f,1520f to 1730f,1800f to 2010f,2080f to 2600f)
        else -> listOf(0f to 340f,405f to 590f,655f to 850f,915f to 1110f,1175f to 1370f,1435f to 1630f,1695f to 1890f,1955f to 2150f,2215f to 2600f)
    }
    private val platforms: List<Platform> get() = when(selectedLevel) {
        1 -> listOf(Platform(275f,115f,125f,"grass"),Platform(680f,165f,135f,"wood"),Platform(980f,125f,150f,"ice"),Platform(1370f,180f,145f,"fragile"),Platform(1640f,125f,155f,"stone"),Platform(1980f,180f,150f,"ice"),Platform(2200f,100f,130f,"fragile"))
        2 -> listOf(Platform(230f,120f,130f,"wood"),Platform(540f,180f,135f,"fragile"),Platform(820f,140f,150f,"ice"),Platform(1120f,195f,140f,"wood"),Platform(1460f,130f,150f,"ice"),Platform(1770f,190f,145f,"fragile"),Platform(2110f,140f,150f,"stone"))
        3 -> listOf(Platform(260f,150f,130f,"stone"),Platform(570f,210f,130f,"fragile"),Platform(860f,145f,145f,"ice"),Platform(1140f,220f,130f,"fragile"),Platform(1450f,155f,145f,"wood"),Platform(1750f,215f,135f,"ice"),Platform(2070f,145f,150f,"stone"),Platform(2350f,195f,130f,"fragile"))
        4 -> listOf(Platform(230f,140f,135f,"ice"),Platform(500f,205f,130f,"fragile"),Platform(790f,155f,145f,"ice"),Platform(1090f,215f,130f,"fragile"),Platform(1390f,145f,140f,"wood"),Platform(1690f,220f,135f,"ice"),Platform(1990f,155f,150f,"fragile"),Platform(2280f,200f,130f,"stone"))
        else -> listOf(Platform(220f,160f,125f,"fragile"),Platform(480f,220f,130f,"ice"),Platform(760f,175f,130f,"fragile"),Platform(1040f,230f,130f,"ice"),Platform(1330f,170f,135f,"fragile"),Platform(1620f,230f,130f,"ice"),Platform(1910f,175f,140f,"fragile"),Platform(2210f,220f,140f,"stone"),Platform(2440f,160f,120f,"wood"))
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val now = System.nanoTime()
        val dt = min(0.032f, (now - previous) / 1_000_000_000f).coerceAtLeast(0f)
        previous = now; time += dt
        val w = width.toFloat(); val h = height.toFloat()
        floor = h * 0.80f
        if (levelSelect) { drawLevelSelect(c,w,h); postInvalidateOnAnimation(); return }
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
        if ((jumpQueued || (jumpHeld && grounded)) && grounded) { vy = -590f; grounded = false; onIce = false }
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
        c.drawColor(when(selectedLevel){2->Color.rgb(173,170,226);3->Color.rgb(106,179,218);4->Color.rgb(79,105,139);5->Color.rgb(58,61,103);else->Color.rgb(116,194,235)})
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
        p.color = when(selectedLevel){2->Color.rgb(101,151,104);3->Color.rgb(61,111,124);4->Color.rgb(74,101,116);5->Color.rgb(79,75,115);else->Color.rgb(67,144,126)}
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
            drawAsset(c, R.drawable.asset_coin, x - 16f * squash, y - 19f, x + 16f * squash, y + 19f)
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
            c.save()
            c.scale(squish, 1f, x, floor - 23f + bob)
            drawAsset(c, R.drawable.asset_slime, x - 30f, floor - 48f + bob, x + 30f, floor - 2f + bob)
            c.restore()
            if (abs(px - ex) < 36f && py + 50f > floor - 43f && py < floor) {
                if (vy > 90f) { vy = -350f } else { lives--; px = max(0f, px - 135f); vx = -vx * .5f; if (lives <= 0) gameOver = true }
            }
        }
    }

    private fun drawAsset(c: Canvas, resourceId: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val drawable = context.getDrawable(resourceId) ?: return
        drawable.setBounds(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
        drawable.draw(c)
    }

    private fun drawPlayer(c: Canvas) {
        val x = px - camera
        val running = abs(vx) > 25f && grounded
        val bob = if (grounded) kotlin.math.sin(time * if (running) 15f else 3f) * if (running) 3f else 1.5f else 0f
        val leg = if (running) kotlin.math.sin(time * 15f) * 2f else 0f
        val y = py + bob
        p.color = Color.argb(55, 22, 34, 43)
        c.drawOval(x - 23f, floor - 5f, x + 23f, floor + 2f, p)
        c.save()
        if (vx < -10f) { c.scale(-1f, 1f, x, y + 45f) }
        c.translate(0f, abs(leg))
        drawAsset(c, R.drawable.asset_player, x - 31f, y - 23f, x + 31f, y + 69f)
        c.restore()
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(220, 25, 37, 54); c.drawRoundRect(16f, 14f, 300f, 60f, 16f, 16f, p)
        p.color = Color.WHITE; p.typeface = Typeface.DEFAULT_BOLD; p.textSize = 19f
        c.drawText("LEVEL $selectedLevel/5   ♥ $lives   ◉ $coins", 27f, 43f, p)
        if (onIce) {
            p.color = Color.argb(210, 33, 110, 156); c.drawRoundRect(w * .40f, 16f, w * .60f, 48f, 14f, 14f, p)
            p.color = Color.WHITE; p.textSize = 15f; c.drawText("SLIPPERY!", w * .40f + 13f, 37f, p)
        }
        // Large touch targets sized for phones; movement controls stay separate from jump.
        val controlBottom = h - 18f
        val controlTop = h - 132f
        drawButton(c, 22f, controlTop, 142f, controlBottom, "◀")
        drawButton(c, 151f, controlTop, 271f, controlBottom, "▶")
        drawButton(c, w - 174f, h - 146f, w - 22f, controlBottom, "JUMP")
    }

    private fun drawButton(c: Canvas, l: Float, t: Float, r: Float, b: Float, text: String) {
        p.color = Color.argb(215, 19, 29, 43); c.drawRoundRect(l, t, r, b, 24f, 24f, p)
        p.color = Color.rgb(100, 190, 125); p.style = Paint.Style.STROKE; p.strokeWidth = 4f
        c.drawRoundRect(l, t, r, b, 24f, 24f, p); p.style = Paint.Style.FILL
        p.color = Color.WHITE; p.textSize = if (text == "JUMP") 24f else 45f; p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(text, (l + r) / 2f - p.measureText(text) / 2f, (t + b) / 2f + p.textSize * .34f, p)
    }

    private fun drawEndScreen(c: Canvas, w: Float, h: Float) {
        p.color = Color.argb(220, 13, 23, 36); c.drawRect(0f, 0f, w, h, p)
        p.color = Color.WHITE; p.textSize = 39f; p.typeface = Typeface.DEFAULT_BOLD
        val title = if (won) (if(selectedLevel==5) "ALL LEVELS CLEAR!" else "LEVEL $selectedLevel CLEAR!") else "GAME OVER"
        c.drawText(title, w / 2f - p.measureText(title) / 2f, h / 2f - 5f, p)
        p.textSize = 19f; val sub = if(won && selectedLevel<5) "Tap untuk Level ${selectedLevel+1}" else if(won) "Tap untuk pilih level" else "Tap untuk coba lagi"
        c.drawText(sub, w / 2f - p.measureText(sub) / 2f, h / 2f + 32f, p)
    }

    private fun beginLevel(level: Int) {
        selectedLevel=level.coerceIn(1,5); levelSelect=false
        px=90f; py=0f; vx=0f; vy=0f; camera=0f; coins=0; lives=3
        collected.clear(); brokenAt.clear(); gameOver=false; won=false; grounded=false; onIce=false
        left=false; right=false; jumpHeld=false; jumpQueued=false; activePointers.clear()
    }
    private fun restart() = beginLevel(selectedLevel)

    private fun drawLevelSelect(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.rgb(17,25,40))
        p.color=Color.rgb(34,49,69); c.drawRoundRect(18f,18f,w-18f,h-18f,24f,24f,p)
        p.color=Color.WHITE; p.typeface=Typeface.DEFAULT_BOLD; p.textSize=min(34f,w*.055f)
        val title="PLATFORMER 2D"; c.drawText(title,w/2f-p.measureText(title)/2f,h*.20f,p)
        p.textSize=min(17f,w*.027f); p.typeface=Typeface.DEFAULT
        val subtitle="PILIH LEVEL  •  5 LEVEL PERTAMA"
        c.drawText(subtitle,w/2f-p.measureText(subtitle)/2f,h*.29f,p)
        val names=listOf("Green Hills","Woodland","Frozen Peaks","Dark Ruins","Final Boss")
        val info=listOf("Pemanasan","Kayu rapuh","Es licin","Lebih sulit","Tantangan akhir")
        val gap=12f; val cw=min(190f,(w-64f-gap*4)/5f); val ch=h*.34f; val total=cw*5f+gap*4f; val start=(w-total)/2f
        for(i in 0..4){
            val x=start+i*(cw+gap); val top=h*.37f
            p.color=if(selectedLevel==i+1) Color.rgb(69,142,108) else Color.rgb(47,64,86)
            c.drawRoundRect(x,top,x+cw,top+ch,15f,15f,p)
            p.color=Color.WHITE; p.typeface=Typeface.DEFAULT_BOLD; p.textSize=min(23f,cw*.20f); c.drawText("${i+1}",x+14f,top+32f,p)
            p.textSize=min(15f,cw*.13f); c.drawText(names[i],x+12f,top+ch*.48f,p)
            p.typeface=Typeface.DEFAULT; p.textSize=min(12f,cw*.105f); c.drawText(info[i],x+12f,top+ch*.68f,p)
            p.color=when(i){0->Color.rgb(82,175,83);1->Color.rgb(164,111,65);2->Color.rgb(111,205,240);3->Color.rgb(105,117,158);else->Color.rgb(153,87,204)}
            c.drawRoundRect(x+12f,top+ch-27f,x+cw-12f,top+ch-14f,6f,6f,p)
        }
        p.color=Color.rgb(208,222,235); p.textSize=min(16f,w*.025f)
        val hint="Sentuh kartu level untuk mulai"; c.drawText(hint,w/2f-p.measureText(hint)/2f,h*.84f,p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w=width.toFloat(); val h=height.toFloat()
        if(levelSelect && e.actionMasked==MotionEvent.ACTION_DOWN){
            val gap=12f; val cw=min(190f,(w-64f-gap*4)/5f); val total=cw*5f+gap*4f; val start=(w-total)/2f
            if(e.y in (h*.34f)..(h*.78f)){
                val idx=((e.x-start)/(cw+gap)).toInt()
                if(idx in 0..4 && e.x>=start+idx*(cw+gap) && e.x<=start+idx*(cw+gap)+cw) beginLevel(idx+1)
            }
            return true
        }
        if((gameOver||won)&&e.actionMasked==MotionEvent.ACTION_DOWN){
            if(won&&selectedLevel<5) beginLevel(selectedLevel+1) else { levelSelect=true; activePointers.clear() }
            return true
        }
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
                val i=e.actionIndex; activePointers[e.getPointerId(i)]=Pair(e.getX(i),e.getY(i))
                if(e.getX(i)>w-184f&&e.getY(i)>h-160f){jumpQueued=true;jumpHeld=true}
            }
            MotionEvent.ACTION_MOVE -> for(i in 0 until e.pointerCount) activePointers[e.getPointerId(i)]=Pair(e.getX(i),e.getY(i))
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP -> activePointers.remove(e.getPointerId(e.actionIndex))
            MotionEvent.ACTION_CANCEL -> activePointers.clear()
        }
        left=activePointers.values.any{it.first in 0f..148f && it.second>h-150f}
        right=activePointers.values.any{it.first in 149f..280f && it.second>h-150f}
        jumpHeld=activePointers.values.any{it.first>w-184f&&it.second>h-160f}
        if(!jumpHeld) jumpQueued=false
        return true
    }
}