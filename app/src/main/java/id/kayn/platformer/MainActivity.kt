package id.kayn.platformer

import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.decorView.systemUiVisibility = 5894 or 1024 or 512; setContentView(GameView()) }
}

private class GameView : View(this@MainActivity) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var playerX = 100f
    private var playerY = 0f
    private var vx = 0f
    private var vy = 0f
    private var grounded = false
    private var left = false
    private var right = false
    private var jump = false
    private var camera = 0f
    private var coins = 0
    private var lives = 3
    private var gameOver = false
    private var last = System.nanoTime()
    private val collected = mutableSetOf<Int>()
    private val coinX = listOf(360f, 610f, 910f, 1210f, 1510f, 1810f)
    private val enemyX = listOf(520f, 1080f, 1650f)
    private val platforms = listOf(floatArrayOf(0f,0f,380f), floatArrayOf(450f,0f,280f), floatArrayOf(800f,0f,310f), floatArrayOf(1190f,0f,300f), floatArrayOf(1570f,0f,360f), floatArrayOf(2020f,0f,420f), floatArrayOf(300f,120f,130f), floatArrayOf(690f,175f,130f), floatArrayOf(1050f,125f,150f), floatArrayOf(1450f,190f,140f), floatArrayOf(1850f,130f,150f))
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val dt = min(0.033f, (System.nanoTime()-last)/1e9f).toFloat(); last=System.nanoTime()
        val w=width.toFloat(); val h=height.toFloat()
        c.drawColor(Color.rgb(130,205,245))
        // Parallax hills and clouds
        p.color=Color.rgb(95,177,196)
        for(i in -1..6){ val x=i*330f-(camera*0.22f%330f); c.drawCircle(x+100,h*0.68f,145f,p); c.drawCircle(x+230,h*0.68f,180f,p) }
        p.color=Color.rgb(65,145,125)
        for(i in -1..7){ val x=i*280f-(camera*0.45f%280f); c.drawCircle(x+100,h*0.79f,120f,p); c.drawCircle(x+230,h*0.79f,145f,p) }
        val floor=h*0.80f
        if(playerY==0f) playerY=floor-58f
        if(left) vx=-250f else if(right) vx=250f else vx*=0.78f
        if(jump && grounded){vy=-590f;grounded=false}; jump=false
        playerX+=vx*dt; vy+=1450f*dt; playerY+=vy*dt
        if(playerX<0)playerX=0f
        if(playerY+58f>=floor){playerY=floor-58f;vy=0f;grounded=true}
        for(a in platforms){
            val px=a[0]-camera; val py=floor-a[1]-18f
            if(px+a[2]>-10 && px<w+10){p.color=Color.rgb(124,78,49);c.drawRoundRect(px,py,px+a[2],py+30f,7f,7f,p);p.color=Color.rgb(83,178,72);c.drawRoundRect(px,py-8f,px+a[2],py+5f,6f,6f,p);p.color=Color.rgb(169,226,91);for(g in 0 until (a[2]/25).toInt()){c.drawCircle(px+g*25+10,py-5,4f,p)}}
            if(a[1]>0 && playerX+34> a[0] && playerX<a[0]+a[2] && vy>=0 && playerY+58>=floor-a[1]-18 && playerY+58<floor-a[1]+15){playerY=floor-a[1]-76;vy=0f;grounded=true}
        }
        camera=max(0f,playerX-w*0.30f)
        for(i in coinX.indices){val x=coinX[i]-camera;val y=floor-85f-(if(i%2==0)35f else 0f);if(i !in collected){p.color=Color.rgb(255,190,30);c.drawCircle(x,y,15f,p);p.color=Color.rgb(255,235,100);c.drawCircle(x,y,9f,p);p.color=Color.rgb(171,104,0);p.textSize=18f;p.typeface=Typeface.DEFAULT_BOLD;c.drawText("$",x-5,y+6,p);if(kotlin.math.abs(playerX-coinX[i])<32 && kotlin.math.abs(playerY+25-y)<48){collected.add(i);coins++}}}
        for(ex in enemyX){val x=ex-camera;if(x>-40&&x<w+40){p.color=Color.rgb(104,206,64);c.drawRoundRect(x-22,floor-44,x+22,floor-5,18f,18f,p);p.color=Color.BLACK;c.drawCircle(x-8,floor-30,4f,p);c.drawCircle(x+8,floor-30,4f,p);p.color=Color.WHITE;c.drawCircle(x-9,floor-31,1.5f,p);c.drawCircle(x+7,floor-31,1.5f,p);if(kotlin.math.abs(playerX-ex)<38 && playerY+50>floor-44){if(vy>80){vy=-330f}else{lives--;playerX=max(0f,playerX-100f);if(lives<=0)gameOver=true}}}}
        // Hero, cartoon hoodie
        val hx=playerX-camera; p.color=Color.rgb(38,39,55);c.drawRoundRect(hx-18,playerY+20,hx+18,playerY+57,9f,9f,p);p.color=Color.rgb(241,190,145);c.drawCircle(hx,playerY+15,17f,p);p.color=Color.rgb(53,35,33);c.drawArc(hx-18,playerY-2,hx+18,playerY+22,180f,180f,true,p);p.color=Color.WHITE;c.drawRoundRect(hx-10,playerY+30,hx+10,playerY+48,4f,4f,p);p.color=Color.BLACK;c.drawCircle(hx+6,playerY+13,2.5f,p);p.color=Color.rgb(30,30,36);c.drawRoundRect(hx-20,playerY+51,hx-2,playerY+59,4f,4f,p);c.drawRoundRect(hx+2,playerY+51,hx+21,playerY+59,4f,4f,p)
        // HUD
        p.color=Color.rgb(24,39,54);c.drawRoundRect(18f,16f,240f,64f,18f,18f,p);p.color=Color.WHITE;p.textSize=22f;p.typeface=Typeface.DEFAULT_BOLD;c.drawText("♥ $lives     ◉ $coins",34f,47f,p)
        drawButton(c,38f,h-92f,108f,h-20f,"◀");drawButton(c,122f,h-92f,192f,h-20f,"▶");drawButton(c,w-128f,h-105f,w-28f,h-20f,"JUMP")
        if(playerX>2240f){p.color=Color.rgb(255,220,80);c.drawRoundRect(w-120,h*0.4f,w-40,h*0.72f,12f,12f,p);p.color=Color.BLACK;c.drawText("GOAL",w-111,h*0.56f,p)}
        if(gameOver){p.color=Color.argb(210,10,18,30);c.drawRect(0f,0f,w,h,p);p.color=Color.WHITE;p.textSize=42f;c.drawText("GAME OVER",w/2-125,h/2,p);p.textSize=20f;c.drawText("Tap untuk main lagi",w/2-90,h/2+38,p)}
        postInvalidateOnAnimation()
    }
    private fun drawButton(c:Canvas,l:Float,t:Float,r:Float,b:Float,s:String){p.color=Color.argb(155,30,40,54);c.drawRoundRect(l,t,r,b,18f,18f,p);p.color=Color.WHITE;p.textSize=if(s=="JUMP")16f else 30f;p.typeface=Typeface.DEFAULT_BOLD;c.drawText(s,(l+r)/2-p.measureText(s)/2,(t+b)/2+10f,p)}
    override fun onTouchEvent(e:MotionEvent):Boolean{
        val w=width.toFloat();val h=height.toFloat()
        if(gameOver && e.action==MotionEvent.ACTION_DOWN){playerX=100f;playerY=h*0.8f-58f;vx=0f;vy=0f;lives=3;coins=0;collected.clear();gameOver=false;return true}
        when(e.action){MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE->{left=e.x<110f&&e.y>h-115f;right=e.x in 110f..215f&&e.y>h-115f;if(e.x>w-150f&&e.y>h-130f)jump=true}
            MotionEvent.ACTION_UP,CANCEL->{left=false;right=false}}
        return true
    }
}