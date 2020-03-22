package com.example.customcamera

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView


class VideoCapture : SurfaceView, SurfaceHolder.Callback {
    private var recorder: MediaRecorder? = null
    private var holder: SurfaceHolder? = null
    private var camera: Camera? = null

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
        init()
    }

    override fun getHolder(): SurfaceHolder {
        return super.getHolder()
    }

    @SuppressLint("NewApi")
    fun init() {
        try {
            recorder = MediaRecorder()
            holder = getHolder()
            holder!!.addCallback(this)
            holder!!.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
            camera = cameraInstance
            if (Build.VERSION.SDK_INT > 7) camera?.setDisplayOrientation(90)
            camera?.unlock()
            recorder!!.setCamera(camera)
            recorder!!.setVideoSource(MediaRecorder.VideoSource.DEFAULT)
            recorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.MPEG_4_SP)
            recorder!!.setOutputFile(videoPath)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun surfaceChanged(
        arg0: SurfaceHolder,
        arg1: Int,
        arg2: Int,
        arg3: Int
    ) {
    }

    override fun surfaceCreated(mHolder: SurfaceHolder) {
        try {
            recorder!!.setPreviewDisplay(mHolder.surface)
            recorder!!.prepare()
            recorder!!.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopCapturingVideo() {
        try {
            recorder!!.stop()
            camera?.lock()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @TargetApi(5)
    override fun surfaceDestroyed(arg0: SurfaceHolder) {
        if (recorder != null) {
            stopCapturingVideo()
            recorder!!.release()
            camera?.lock()
            camera?.release()
            recorder = null
        }
    }// Camera is not available (in use or does not exist)

    // attempt to get a Camera instance
    private val cameraInstance: Camera?
        private get() {
            var c: Camera? = null
            try {
                c = Camera.open() // attempt to get a Camera instance
            } catch (e: Exception) { // Camera is not available (in use or does not exist)
            }
            return c
        }

    companion object {
        var videoPath: String = Environment.getExternalStorageDirectory()
            .absolutePath + "/${System.currentTimeMillis()}.mp4"
    }
}