package com.example.livestreamingagora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.livestreamingagora.databinding.ActivityMainBinding
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.video.VideoCanvas

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val myAppId = "ea3bfb092005472d861486abcc06fcb2"
    private val channelName = "123"
    private val token =
        "007eJxTYOj8/sZSXIyz/r0ZZ8T8LIPox6+mOmpqRD3d1KCfobFPd4MCQ2qicVJakoGlkYGBqYm5UYqFmaGJhVliUnKygVlacpJRF3NnekMgI8O0qCkMjFAI4jMzGBoZMzAAAK29HJc="
    private val permissionReqId = 22
    private lateinit var mRtcEngine: RtcEngine
    private var isJoined = false
    private var isMuted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (checkPermissions()) {
            initializeAgora()
        } else {
            ActivityCompat.requestPermissions(
                this,
                getRequiredPermissions(),
                permissionReqId
            )
        }
    }

    private fun initializeAgora() {
        setupVideoSDKEngine()
        setupUI()
    }

    private fun setupUI() {
        binding.startStopButton.setOnClickListener {
            if (!isJoined) {
                joinChannel()
            } else {
                leaveChannel()
            }
        }

        binding.switchCameraButton.setOnClickListener {
            mRtcEngine.switchCamera()
        }

        binding.muteButton.setOnClickListener {
            isMuted = !isMuted
            mRtcEngine.muteLocalAudioStream(isMuted)
            (it as Button).text = if (isMuted) "Unmute" else "Mute"
        }
    }

    private fun setupVideoSDKEngine() {
        try {
            val config = RtcEngineConfig().apply {
                mContext = applicationContext
                mAppId = myAppId
                mEventHandler = mRtcEventHandler
            }
            mRtcEngine = RtcEngine.create(config)
            mRtcEngine.enableVideo()
        } catch (e: Exception) {
            throw RuntimeException("Check the error.")
        }
    }

    private fun joinChannel() {
        if (checkPermissions()) {
            setupLocalVideo()
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
            mRtcEngine.startPreview()
            mRtcEngine.joinChannel(token, channelName, 0, options)
        }
    }

    private fun setupLocalVideo() {
        val surfaceView = SurfaceView(applicationContext)
        surfaceView.setZOrderMediaOverlay(true)
        binding.localVideoViewContainer.addView(surfaceView)
        mRtcEngine.setupLocalVideo(
            VideoCanvas(
                surfaceView,
                VideoCanvas.RENDER_MODE_FIT,
                0
            )
        )
    }

    private fun setupRemoteVideo(uid: Int) {
        val surfaceView = SurfaceView(applicationContext).apply {
            setZOrderMediaOverlay(true)
        }
        binding.remoteVideoViewContainer.addView(surfaceView)
        mRtcEngine.setupRemoteVideo(VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid))
    }

    private fun leaveChannel() {
        mRtcEngine.leaveChannel()
        binding.localVideoViewContainer.removeAllViews()
        binding.remoteVideoViewContainer.removeAllViews()
        isJoined = false
        binding.startStopButton.text = "Start Stream"
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            super.onJoinChannelSuccess(channel, uid, elapsed)
            runOnUiThread {
                binding.startStopButton.text = "End Stream"
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
                binding.remoteVideoViewContainer.removeAllViews()
            }
        }
    }

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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermissions()) {
            initializeAgora()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mRtcEngine.stopPreview()
        mRtcEngine.leaveChannel()
    }
}