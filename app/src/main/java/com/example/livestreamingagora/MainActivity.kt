package com.example.livestreamingagora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
        "007eJxTYMiesOpbQ4H2yiPPjy18yGBRdOQJf0iC+y/eBjUjHa6yot0KDKmJxklpSQaWRgYGpibmRikWZoYmFmaJScnJBmZpyUlGV2/1pjcEMjJIy1iwMjJAIIjPzGBoZMzAAAA2dx3g"
    private val permissionReqId = 22
    private lateinit var mRtcEngine: RtcEngine
    private var isJoined = false
    private var isMuted = false
    private var isHost = false

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
        showRoleSelectionDialog()
    }

    private fun showRoleSelectionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Role")
            .setMessage("Would you like to join as a host or audience member?")
            .setPositiveButton("Host") { _, _ ->
                isHost = true
                checkAndRequestPermissions()
            }
            .setNegativeButton("Audience") { _, _ ->
                isHost = false
                checkAndRequestPermissions()
            }
            .setCancelable(false)
            .show()
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
        if (isHost) {
            binding.apply {
                switchCameraButton.visibility = View.VISIBLE
                muteButton.visibility = View.VISIBLE
                startStopButton.text = "Start Stream"
            }
        } else {
            binding.apply {
                switchCameraButton.visibility = View.GONE
                muteButton.visibility = View.GONE
                startStopButton.text = "Join Stream"
            }
        }

        binding.startStopButton.setOnClickListener {
            if (!isJoined) {
                joinChannel()
            } else {
                leaveChannel()
            }
        }

        binding.switchCameraButton.visibility = if (isHost) View.VISIBLE else View.GONE

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
            if (isHost) {
                setupLocalVideo()
            }

            val options = ChannelMediaOptions().apply {
                clientRoleType = if (isHost) {
                    Constants.CLIENT_ROLE_BROADCASTER
                } else {
                    Constants.CLIENT_ROLE_AUDIENCE
                }
                channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING
                audienceLatencyLevel = Constants.AUDIENCE_LATENCY_LEVEL_ULTRA_LOW_LATENCY

                publishMicrophoneTrack = isHost
                publishCameraTrack = isHost

                autoSubscribeAudio = true
                autoSubscribeVideo = true
            }

            if (isHost) {
                mRtcEngine.startPreview()
            }
            mRtcEngine.joinChannel(token, channelName, 0, options)
        }
    }

    private fun setupLocalVideo() {
        val surfaceView = SurfaceView(applicationContext)
        surfaceView.setZOrderMediaOverlay(true)
        binding.localVideoViewContainer.removeAllViews()
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
        if (isHost) {
            val surfaceView = SurfaceView(applicationContext).apply {
                setZOrderMediaOverlay(true)
            }
            mRtcEngine.setupRemoteVideo(
                VideoCanvas(
                    surfaceView,
                    VideoCanvas.RENDER_MODE_FIT,
                    uid
                )
            )
        }
    }

    private fun leaveChannel() {
        if (isHost) {
            mRtcEngine.stopPreview()
        }
        mRtcEngine.leaveChannel()

        binding.localVideoViewContainer.removeAllViews()

        isJoined = false
        binding.startStopButton.text = if (isHost) "Start Stream" else "Join Stream"
    }

    private val mRtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            runOnUiThread {
                isJoined = true
                binding.startStopButton.text = if (isHost) "End Stream" else "Leave Stream"
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            runOnUiThread {
                if (!isHost) {
                    val surfaceView = SurfaceView(applicationContext)
                    binding.localVideoViewContainer.removeAllViews()
                    binding.localVideoViewContainer.addView(surfaceView)
                    mRtcEngine.setupRemoteVideo(
                        VideoCanvas(
                            surfaceView,
                            VideoCanvas.RENDER_MODE_FIT,
                            uid
                        )
                    )
                } else {
                    setupRemoteVideo(uid)
                }
            }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            runOnUiThread {
                binding.localVideoViewContainer.removeAllViews()
            }
        }

        override fun onError(err: Int) {
            runOnUiThread {
                showError("Error code: $err")
            }
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (isHost) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf()
            }
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
        } else {
            showError("Permissions not granted")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isHost) {
            mRtcEngine.stopPreview()
        }
        mRtcEngine.leaveChannel()
        RtcEngine.destroy()
    }
}