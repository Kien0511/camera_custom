package com.example.customcamera

import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.vision.CameraSource
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.Detector.Detections
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import java.io.IOException
import java.lang.reflect.Field


class VideoActivity : AppCompatActivity() {
    private lateinit var barcodeDetector: BarcodeDetector
    private lateinit var cameraSource: CameraSource
    private lateinit var cameraView: SurfaceView
    private lateinit var barcodeInfo: TextView

    private lateinit var mediaRecorder: MediaRecorder
    private val MAX_TIME = 30000

    private lateinit var btnCapture: Button

    private var checkRecord = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        cameraView = findViewById(R.id.camera_view)
        barcodeInfo = findViewById(R.id.code_info)
        btnCapture = findViewById(R.id.btnCapture)

        barcodeDetector = BarcodeDetector.Builder(this)
            .setBarcodeFormats(Barcode.QR_CODE)
            .build()

        cameraSource = CameraSource.Builder(this, barcodeDetector)
            .setRequestedPreviewSize(1920, 1080)
            .setAutoFocusEnabled(true)
            .setFacing(CameraSource.CAMERA_FACING_BACK)
            .setRequestedFps(30.0f)
            .build()
        cameraView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                try {
                    holder.setKeepScreenOn(true)
                    cameraSource.start(holder)
                    mediaRecorder = MediaRecorder()
                    mediaRecorder.setPreviewDisplay(holder.surface)
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT)
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT)
                    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
                    mediaRecorder.setMaxDuration(MAX_TIME)
//        mediaRecorder.setOnInfoListener(m_BeMeSelf)
                    mediaRecorder.setVideoFrameRate(15)
                    mediaRecorder.setOrientationHint(90)
//                    mediaRecorder.setVideoSize(1080,1920)
                    mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory().absolutePath + "/${System.currentTimeMillis()}.mp4")
                    mediaRecorder.prepare()
                    Log.e("aaa","prepare ok")
                } catch (ie: IOException) {
                    Log.e("CAMERA SOURCE", ie.message)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                cameraSource.stop()
            }
        })

        barcodeDetector.setProcessor(object : Detector.Processor<Barcode> {
            override fun release() {}
            override fun receiveDetections(detections: Detections<Barcode>) {
                val barcodes = detections.detectedItems
                if (barcodes.size() != 0) {
                    barcodeInfo.post(Runnable // Use the post method of the TextView
                    {
                        barcodeInfo.setText( // Update the TextView
                            barcodes.valueAt(0).displayValue
                        )
                    })
                }
            }
        })

        btnCapture.setOnClickListener {
            checkRecord = !checkRecord
            if (checkRecord) {
                btnCapture.setText("stop")
                mediaRecorder.start()
            } else {
                btnCapture.setText("start")
                mediaRecorder.stop()
                mediaRecorder.release()
                Toast.makeText(this@VideoActivity, "record complete", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
