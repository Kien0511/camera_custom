package com.example.customcamera

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.opengl.GLException
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.customcamera.camerarecorder.CameraRecordListener
import com.example.customcamera.camerarecorder.CameraRecorder
import com.example.customcamera.camerarecorder.CameraRecorderBuilder
import com.example.customcamera.camerarecorder.LensFacing
import com.example.customcamera.widget.Filters
import com.example.customcamera.widget.SampleGLView
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.IntBuffer
import java.text.SimpleDateFormat
import java.util.*
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.opengles.GL10

class LibCamera : AppCompatActivity() {
    private var sampleGLView: SampleGLView? = null
    protected var cameraRecorder: CameraRecorder? = null
    private var filepath: String? = null
    private val recordBtn: TextView? = null
    protected var lensFacing: LensFacing = LensFacing.BACK
    protected var cameraWidth = 1280
    protected var cameraHeight = 720
    protected var videoWidth = 720
    protected var videoHeight = 1080
    private val filterDialog: AlertDialog? = null
    private var toggleClick = false
    private val handler = Handler()
    private var runnable: Runnable? = null

    private lateinit var imvPreview: ImageView
    private lateinit var imvCancel: ImageView
    private lateinit var imvCapture: ImageView
    private lateinit var imvSave: ImageView
    private lateinit var imvPlay: ImageView

    private var bitmap: Bitmap? = null
    private var isRecordVideo = true
    private var checkRecord = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lib_camera)
        imvPreview = findViewById(R.id.imvPreview)
        imvCancel = findViewById(R.id.imvCancel)
        imvCapture = findViewById(R.id.imvCapture)
        imvSave = findViewById(R.id.imvSave)
        imvPlay = findViewById(R.id.imvPlay)

        if (isRecordVideo) {
            imvCapture.setImageResource(R.drawable.ic_record_start)
        } else {
            imvCapture.setImageResource(R.drawable.ic_capture_image)
        }

        imvCapture.setOnClickListener {
            if (!isRecordVideo) {
                captureBitmap(object : BitmapReadyCallbacks {
                    override fun onBitmapReady(bitmap: Bitmap?) {
                        Handler().post {
                            this@LibCamera.bitmap = bitmap
                            imvPreview.setImageBitmap(bitmap)
                            imvPreview.visibility = View.VISIBLE
                            releaseCamera()
                            imvCapture.visibility = View.GONE
                            imvSave.visibility = View.VISIBLE
                            imvCancel.visibility = View.VISIBLE
                        }
                    }
                })
            } else {
                checkRecord = !checkRecord
                if (checkRecord) {
                    imvCapture.setImageResource(R.drawable.ic_record_stop)
                    filepath = getVideoFilePath()
                    cameraRecorder!!.start(filepath)
                    runnable = object : Runnable {
                        override fun run() {
                            val file = File(filepath)
                            if (file.exists() && file.length() / 1024 > 25000) {
                                imvCapture.performClick()
                                handler.removeCallbacks(this)
                                Log.e("aaa", "Stop")
                            } else {
                                handler.postDelayed(this, 1000)
                                Log.e("aaa", "Run");
                            }
                        }
                    }
                    handler.post(runnable);
                } else {
                    cameraRecorder!!.stop()
                    if (runnable != null) {
                        handler.removeCallbacks(runnable)
                    }
                    imvPlay.visibility = View.VISIBLE
                    releaseCamera()

                    Glide.with(this@LibCamera)
                        .load(Uri.fromFile(File(filepath)))
                        .into(imvPreview)
                    imvPreview.visibility = View.VISIBLE

                    imvCapture.setImageResource(R.drawable.ic_record_start)
                    imvCapture.visibility = View.GONE
                    imvCancel.visibility = View.VISIBLE
                    imvSave.visibility = View.VISIBLE
                }
            }
        }

        imvCancel.setOnClickListener {
            setUpCamera()
            imvCancel.visibility = View.GONE
            imvPreview.visibility = View.GONE
            imvSave.visibility = View.GONE
            imvCapture.visibility = View.VISIBLE
            imvPlay.visibility = View.GONE
            bitmap = null
        }

        imvSave.setOnClickListener {
            Handler().post {
                bitmap?.let {
                    val imagePath: String = getImageFilePath()!!
                    saveAsPngImage(it, imagePath)
                    exportPngToGallery(
                        this@LibCamera,
                        imagePath
                    )
                }
                setUpCamera()
                imvCancel.visibility = View.GONE
                imvPreview.visibility = View.GONE
                imvSave.visibility = View.GONE
                imvCapture.visibility = View.VISIBLE
                imvPlay.visibility = View.GONE
                bitmap = null
            }
        }

        imvPlay.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
            intent.setDataAndType(Uri.parse(filepath), "video/mp4");
            startActivity(intent);
        }
    }


    override fun onResume() {
        super.onResume()
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setUpCamera()
    }

    override fun onStop() {
        super.onStop()
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        releaseCamera()
    }

    private fun releaseCamera() {
        if (sampleGLView != null) {
            sampleGLView?.onPause()
        }
        if (cameraRecorder != null) {
            cameraRecorder!!.stop()
            cameraRecorder!!.release()
            cameraRecorder = null
        }
        if (sampleGLView != null) {
            (findViewById(R.id.framePreview) as FrameLayout).removeView(sampleGLView)
            sampleGLView = null
        }
    }


    private fun setUpCameraView() {
        runOnUiThread {
            val frameLayout: FrameLayout = findViewById(R.id.framePreview)
            frameLayout.removeAllViews()
            sampleGLView = null
            sampleGLView = SampleGLView(applicationContext)
            //            sampleGLView.setTouchListener((event, width, height) -> {
            //                if (cameraRecorder == null) return;
            //                cameraRecorder.changeManualFocusPoint(event.getX(), event.getY(), width, height);
            //            });
            frameLayout.addView(sampleGLView)
        }
    }


    private fun setUpCamera() {
        setUpCameraView()
        cameraRecorder = CameraRecorderBuilder(this, sampleGLView) //.recordNoFilter(true)
            .cameraRecordListener(object : CameraRecordListener {
                override fun onGetFlashSupport(flashSupport: Boolean) {
//                    runOnUiThread { findViewById(R.id.btn_flash).setEnabled(flashSupport) }
                }

                override fun onRecordComplete() {
                    exportMp4ToGallery(applicationContext, filepath!!)
                }

                override fun onRecordStart() {}
                override fun onError(exception: Exception) {
                    Log.e("CameraRecorder", exception.toString())
                }

                override fun onCameraThreadFinish() {
                    if (toggleClick) {
                        runOnUiThread { setUpCamera() }
                    }
                    toggleClick = false
                }
            })
            .videoSize(videoWidth, videoHeight)
            .cameraSize(cameraWidth, cameraHeight)
            .lensFacing(lensFacing)
            .build()
    }

    private fun changeFilter(filters: Filters) {
        cameraRecorder!!.setFilter(Filters.getFilterInstance(filters, applicationContext))
    }


    private interface BitmapReadyCallbacks {
        fun onBitmapReady(bitmap: Bitmap?)
    }

    private fun captureBitmap(bitmapReadyCallbacks: BitmapReadyCallbacks) {
        sampleGLView!!.queueEvent {
            val egl = EGLContext.getEGL() as EGL10
            val gl = egl.eglGetCurrentContext().gl as GL10
            val snapshotBitmap = createBitmapFromGLSurface(
                sampleGLView!!.measuredWidth,
                sampleGLView!!.measuredHeight,
                gl
            )
            runOnUiThread { bitmapReadyCallbacks.onBitmapReady(snapshotBitmap) }
        }
    }

    private fun createBitmapFromGLSurface(w: Int, h: Int, gl: GL10): Bitmap? {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)
        try {
            gl.glReadPixels(0, 0, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            var texturePixel: Int
            var blue: Int
            var red: Int
            var pixel: Int
            for (i in 0 until h) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0 until w) {
                    texturePixel = bitmapBuffer[offset1 + j]
                    blue = texturePixel shr 16 and 0xff
                    red = texturePixel shl 16 and 0x00ff0000
                    pixel = texturePixel and -0xff0100 or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            Log.e("CreateBitmap", "createBitmapFromGLSurface: " + e.message, e)
            return null
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }

    fun saveAsPngImage(bitmap: Bitmap, filePath: String?) {
        try {
            val file = File(filePath)
            val outStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.close()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }


    fun exportMp4ToGallery(
        context: Context,
        filePath: String
    ) {
        val values = ContentValues(2)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.DATA, filePath)
        context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        )
        context.sendBroadcast(
            Intent(
                Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://$filePath")
            )
        )
    }

    fun getVideoFilePath(): String? {
        return getAndroidMoviesFolder().absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss").format(
            Date()
        ) + "cameraRecorder.mp4"
    }

    fun getAndroidMoviesFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
    }

    private fun exportPngToGallery(
        context: Context,
        filePath: String
    ) {
        val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        val f = File(filePath)
        val contentUri = Uri.fromFile(f)
        mediaScanIntent.data = contentUri
        context.sendBroadcast(mediaScanIntent)
    }

    fun getImageFilePath(): String? {
        return getAndroidImageFolder().absolutePath + "/" + SimpleDateFormat("yyyyMM_dd-HHmmss").format(
            Date()
        ) + "customCamera.png"
    }

    fun getAndroidImageFolder(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    }

}
