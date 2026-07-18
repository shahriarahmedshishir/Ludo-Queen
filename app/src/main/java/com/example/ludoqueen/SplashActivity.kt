package com.example.ludoqueen

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val ivIcon: ImageView = findViewById(R.id.ivLudoIcon)
        val tvTitle: TextView = findViewById(R.id.tvSplashTitle)

        ivIcon.scaleX = 0f
        ivIcon.scaleY = 0f
        ivIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700)
            .setInterpolator(OvershootInterpolator())
            .start()

        val rotate = ObjectAnimator.ofFloat(ivIcon, "rotation", 0f, 360f)
        rotate.duration = 2200
        rotate.repeatCount = ObjectAnimator.INFINITE
        rotate.startDelay = 700
        rotate.start()

        tvTitle.animate()
            .alpha(1f)
            .setStartDelay(600)
            .setDuration(600)
            .start()

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, PlayerSelectorActivity::class.java))
            finish()
        }, 2500)
    }
}