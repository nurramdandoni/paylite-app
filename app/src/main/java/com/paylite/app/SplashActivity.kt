package com.paylite.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoView = VideoView(this)
        setContentView(videoView)

        val uri = Uri.parse("android.resource://$packageName/${R.raw.splash}")
        videoView.setVideoURI(uri)

        videoView.setOnCompletionListener {

            openMain()

        }

        videoView.start()

    }

    private fun openMain(){

        val intentMain = Intent(this, MainActivity::class.java)

        // TERUSKAN SEMUA INTENT
        intentMain.action = intent.action
        intentMain.data = intent.data
        intentMain.putExtras(intent ?: return)

        startActivity(intentMain)
        finish()

    }
}