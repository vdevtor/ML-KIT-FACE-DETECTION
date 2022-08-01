package com.example.mlkitpoc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.mlkitpoc.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.face.FaceContour.*
import java.io.File
import java.io.IOException
import java.io.InputStream


class MainActivity : AppCompatActivity() {

    //binding
    private lateinit var binding: ActivityMainBinding

    //ml kit variables
    private lateinit var highAccuracy: FaceDetectorOptions
    private lateinit var realTimeOpts: FaceDetectorOptions
    private var latestTmpUri: Uri? = null
    private var image: InputImage? = null
    private var imageBitmap: Bitmap? = null

    //camera and galery actions

    private val takeImageResult =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {

                latestTmpUri?.let { uri ->
                    image = InputImage.fromFilePath(this, uri)
                    imageBitmap = handleSamplingAndRotationBitmap(this, uri)
                    binding.ivPhoto.setImageBitmap(imageBitmap)
                    val detector = FaceDetection.getClient(highAccuracy)
                    proccessImage(detector)
                }
            }
        }

    private val selectImageFromGalleryResult =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->

            uri?.let {

                image = InputImage.fromFilePath(this, uri)
                imageBitmap = handleSamplingAndRotationBitmap(this, uri)
                binding.ivPhoto.setImageBitmap(imageBitmap)
                val detector = FaceDetection.getClient(highAccuracy)
                proccessImage(detector)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        verifyStoragePermissions()
        presetAccuracyMode()
        setCountorMode()
        initButtons()
        checkBackgroundPermissions()
    }

    private fun verifyStoragePermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED

            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

            || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                0
            )
        }
    }

    private fun checkBackgroundPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                0
            )
        }
    }


    private fun initButtons() {
        binding.btnPickPhoto.setOnClickListener { selectImageFromGallery() }
        binding.btnTakePhoto.setOnClickListener { takeImage() }
    }

    //picking image from camera
    private fun takeImage() {
        lifecycleScope.launchWhenStarted {
            getTmpFileUri().let { uri ->
                latestTmpUri = uri
                takeImageResult.launch(uri)
            }
        }
    }

    //picking image from gallery
    private fun selectImageFromGallery() = selectImageFromGalleryResult.launch("image/*")

    //getting temp uri file
    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile("tmp_image_file", ".png", cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }

        return FileProvider.getUriForFile(
            applicationContext,
            "${BuildConfig.APPLICATION_ID}.provider",
            tmpFile
        )
    }


    private fun presetAccuracyMode() {
        highAccuracy = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()

    }

    private fun setCountorMode() {
        realTimeOpts =
            FaceDetectorOptions.Builder().setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
    }

    private fun proccessImage(faceDetector: FaceDetector) {
        image?.let { imageToProcess ->
            faceDetector.process(imageToProcess)
                .addOnSuccessListener {
                    Toast.makeText(this, "BOA VITOR total faces = ${it.size}", Toast.LENGTH_SHORT)
                        .show()
                    displayFaces(it)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun displayFaces(faces: List<Face>) {

        val bitmap = Bitmap.createBitmap(
            imageBitmap!!.width, imageBitmap!!.height, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        imageBitmap?.let {
            canvas.drawBitmap(it, 0F, 0F, null)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 20.0f
        for (face in faces) {
            val bounds: Rect = face.boundingBox
            canvas.drawRect(bounds, paint)

            //eyebrows
            val dotPaint = Paint()
            dotPaint.color = Color.RED
            dotPaint.style = Paint.Style.FILL
            dotPaint.strokeWidth = 12F
            val linePaint = Paint()
            linePaint.color = Color.GREEN
            linePaint.style = Paint.Style.STROKE
            linePaint.strokeWidth = 12F


            val leftEyebrowBottomContours = face.getContour(LEFT_EYEBROW_TOP)?.points
            for ((i, contour) in leftEyebrowBottomContours!!.withIndex()) {
                if (i != leftEyebrowBottomContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, leftEyebrowBottomContours[i + 1].x, leftEyebrowBottomContours[i + 1].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }


            val rightEyebrowTopContours = face.getContour(RIGHT_EYEBROW_TOP)?.points
            for ((i, contour) in rightEyebrowTopContours!!.withIndex()) {
                if (i != rightEyebrowTopContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, rightEyebrowTopContours[i + 1].x, rightEyebrowTopContours[i + 1].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }


            //eyes

            val leftEyeContours = face.getContour(LEFT_EYE)?.points
            for ((i, contour) in leftEyeContours!!.withIndex()) {
                if (i != leftEyeContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, leftEyeContours[i + 1].x, leftEyeContours[i + 1].y, linePaint)
                else
                    canvas.drawLine(contour.x, contour.y, leftEyeContours[0].x, leftEyeContours[0].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }

            val rightEyeContours = face.getContour(RIGHT_EYE)?.points
            for ((i, contour) in rightEyeContours!!.withIndex()) {
                if (i != rightEyeContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, rightEyeContours[i + 1].x, rightEyeContours[i + 1].y, linePaint)
                else
                    canvas.drawLine(contour.x, contour.y, rightEyeContours[0].x, rightEyeContours[0].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }

            //Nose

            val noseBridgeContours = face.getContour(NOSE_BRIDGE)?.points
            for ((i, contour) in noseBridgeContours!!.withIndex()) {
                if (i != noseBridgeContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, noseBridgeContours[i + 1].x, noseBridgeContours[i + 1].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }

            val noseBottomContours = face.getContour(NOSE_BOTTOM)?.points
            for ((i, contour) in noseBottomContours!!.withIndex()) {
                if (i != noseBottomContours.lastIndex)
                    canvas.drawLine(contour.x, contour.y, noseBottomContours[i + 1].x, noseBottomContours[i + 1].y, linePaint)
                canvas.drawCircle(contour.x, contour.y, 4F, dotPaint)
            }
        }

        Glide.with(this).load(bitmap).into(binding.ivPhoto)
    }

    @Throws(IOException::class)
    fun handleSamplingAndRotationBitmap(context: Context, selectedImage: Uri): Bitmap? {

        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        var imageStream: InputStream? = context.contentResolver.openInputStream(selectedImage)
        BitmapFactory.decodeStream(imageStream, null, options)
        imageStream?.close()

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        imageStream = context.contentResolver.openInputStream(selectedImage)
        var img = BitmapFactory.decodeStream(imageStream, null, options)
        img = rotateImageIfRequired(context, img!!, selectedImage)
        return img
    }


    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap? {
        val input = context.contentResolver.openInputStream(selectedImage)
        val ei: ExifInterface = if (Build.VERSION.SDK_INT > 23)
            ExifInterface(input!!)
        else
            ExifInterface(selectedImage.path!!)

        return when (ei.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 ->
                rotateImage(img, 90)
            ExifInterface.ORIENTATION_ROTATE_180 ->
                rotateImage(img, 180)
            ExifInterface.ORIENTATION_ROTATE_270 ->

                rotateImage(img, 270)
            else ->
                img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Int): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }


//    @Throws(IOException::class)
//    private fun getBitmapFromUri(uri: Uri): Bitmap? {
//        val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r")
//        val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
//        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
//        parcelFileDescriptor.close()
//        return image
//    }
}