package com.example.livestreamingagora

import android.os.Bundle
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.agora.rtc2.RtcEngineConfig

class MainActivity : AppCompatActivity() {
    private val myAppId = "ea3bfb092005472d861486abcc06fcb2"
    private val channelName = "123"
    private val token = "007eJxTYOj8/sZSXIyz/r0ZZ8T8LIPox6+mOmpqRD3d1KCfobFPd4MCQ2qicVJakoGlkYGBqYm5UYqFmaGJhVliUnKygVlacpJRF3NnekMgI8O0qCkMjFAI4jMzGBoZMzAAAK29HJc="

    private lateinit var mRtcEngine: RtcEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (checkPermissions()) {
            initializeAndJoinChannel()
        } else {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), permissionReqId)
        }
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Join channel success", Toast.LENGTH_SHORT).show()
            }
        }
        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                setupRemoteVideo(uid)
            }
        }
        override fun onUserOffline(uid: Int, reason: Int) {
            super.onUserOffline(uid, reason)
            runOnUiThread {
                Toast.makeText(this@MainActivity, "User offline: $uid", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeAndJoinChannel() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = myAppId
                mEventHandler = mRtcEventHandler
            }
            mRtcEngine = RtcEngine.create(config)
        } catch (e: Exception) {
            throw RuntimeException("Check the error.")
        }
        mRtcEngine.enableVideo()
        mRtcEngine.startPreview()
        val container = findViewById<FrameLayout>(R.id.local_video_view_container)
        val surfaceView = SurfaceView(applicationContext)
        container.addView(surfaceView)
        mRtcEngine.setupLocalVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, 0))
        val options = ChannelMediaOptions().apply {
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
            audienceLatencyLevel = Constants.AUDIENCE_LATENCY_LEVEL_ULTRA_LOW_LATENCY
            // Publish the audio captured by the microphone
            publishMicrophoneTrack = true
            // Publish the video captured by the camera
            publishCameraTrack = true
            // Automatically subscribe to all audio streams
            autoSubscribeAudio = true
            // Automatically subscribe to all video streams
            autoSubscribeVideo = true
        }
        mRtcEngine.joinChannel(token, channelName, 0, options)
    }

    private fun setupRemoteVideo(uid: Int) {
        val container = findViewById<FrameLayout>(R.id.remote_video_view_container)
        val surfaceView = SurfaceView(applicationContext).apply {
            setZOrderMediaOverlay(true)
        }
        container.addView(surfaceView)
        mRtcEngine.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid))
    }

    private val permissionReqId = 22
    private fun getRequiredPermissions(): Array<String> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
            )
        }
    }

    private fun checkPermissions(): Boolean {
        for (permission in getRequiredPermissions()) {
            val permissionCheck = ContextCompat.checkSelfPermission(this, permission)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermissions()) {
            initializeAndJoinChannel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRtcEngine.stopPreview()
        mRtcEngine.leaveChannel()
    }
}