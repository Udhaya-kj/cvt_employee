package com.cvt.employee

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cvt.employee.activity.BaseActivity
import com.cvt.employee.common.Constants
import com.cvt.employee.common.Global
import com.cvt.employee.common.ProviderLocation
import com.cvt.employee.database.Employee
import com.cvt.employee.database.EmployeeDatabase
import com.cvt.employee.databinding.ActivityMainBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.Gson
import com.sahana.horizontalcalendar.OnDateSelectListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class MainActivity : BaseActivity(), OnMapReadyCallback {
    companion object {
        private const val CAMERA_REQUEST_CODE = 111
        private const val CAPTURE_IMAGE_REQUEST = 1
    }

    var photoFile: File? = null
    var mCurrentPhotoPath: String? = null
    private var mProviderLocation: ProviderLocation? = null
    private lateinit var mBinding: ActivityMainBinding
    private var mMap: GoogleMap? = null

    private var updateProductiveReceiver: BroadcastReceiver? = null
    private var updateBreakReceiver: BroadcastReceiver? = null
    private val employeeDatabase by lazy { EmployeeDatabase.getDatabase(this).employeeDao() }

    @SuppressLint("SimpleDateFormat")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ContextCompat.getColor(this, R.color.colorPrimary)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        mBinding.calenderView.setOnDateSelectListener(OnDateSelectListener { dateModel ->
            Log.d("TAG", "onCreate:date--- " + dateModel.month)
            /* mDateTextView.setText(
                 if (dateModel != null) dateModel.day.toString() + " " + dateModel.dayOfWeek + " " + dateModel.month + "," + dateModel.year else ""
             )*/
        })
        val isProductiveRunning = checkServiceRunning(ProductiveTimerService::class.java)
        val isBreakRunning = checkServiceRunning(BreakTimerService::class.java)
        Log.d("TAG", "onCreate:isServiceRunning $isProductiveRunning $isBreakRunning")
        if (isProductiveRunning) {
            mBinding.btnCheckInOut.text = getString(R.string.check_out)
            mBinding.btnCheckInOut.background =
                ContextCompat.getDrawable(this, R.drawable.curved_button_bg_orange)
        }
        if (isBreakRunning) {
            mBinding.tvApplyBreak.text = getString(R.string.end_break)
            mBinding.tvApplyBreak.background =
                ContextCompat.getDrawable(this@MainActivity, R.drawable.layout_bg_blue)
        }
        CoroutineScope(Dispatchers.Default).launch {
            val employee = employeeDatabase.getEmployee()
            if (employee != null && employee.productiveTime!! > 0) {
                val hours = employee.productiveTime / 3600
                val minutes = employee.productiveTime % 3600 / 60
                val seconds = employee.productiveTime % 60
                mBinding.tvProductiveTime.text =
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                mBinding.btnCheckInOut.text = getString(R.string.check_out)
                mBinding.btnCheckInOut.background =
                    ContextCompat.getDrawable(this@MainActivity, R.drawable.curved_button_bg_orange)
            }
            if (employee != null && employee.breakTime!! > 0) {
                val hours = employee.breakTime / 3600
                val minutes = employee.breakTime % 3600 / 60
                val seconds = employee.breakTime % 60
                mBinding.tvBreakTime.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
            if (employee != null && employee.checkInTime!!.isNotEmpty()) {
                mBinding.tvCheckIn.text = employee.checkInTime
            }
            if (employee != null && employee.checkOutTime!!.isNotEmpty()&& employee.checkOutTime!="0") {
                mBinding.tvCheckOut.text = employee.checkOutTime
            }
        }

        mBinding.btnCheckInOut.setOnClickListener {
            if (mBinding.btnCheckInOut.text == getString(R.string.check_in)) {
                val time = Calendar.getInstance().time
                val formatter = SimpleDateFormat("HH:mm a", Locale.US)
                val currentTime = formatter.format(time)
                mBinding.tvCheckIn.text = currentTime
                CoroutineScope(Dispatchers.IO).launch {
                    val employee = employeeDatabase.getEmployee()
                    if (employee == null) {
                        val employeeData = Employee(0, 0, 0, currentTime, "0")
                        employeeDatabase.insertEmployee(employeeData)
                    }
                }
                startProductiveService()
                mBinding.btnCheckInOut.text = getString(R.string.check_out)
                mBinding.btnCheckInOut.background =
                    ContextCompat.getDrawable(this, R.drawable.curved_button_bg_orange)
            } else if (mBinding.btnCheckInOut.text == getString(R.string.check_out)) {
                if (mBinding.tvApplyBreak.text == getString(R.string.end_break)) {
                    Global.showSnackBar(this, mBinding.root, getString(R.string.check_warning))
                } else {
                    val time = Calendar.getInstance().time
                    val formatter = SimpleDateFormat("HH:mm a", Locale.US)
                    val currentTime = formatter.format(time)
                    mBinding.tvCheckOut.text = currentTime
                    mBinding.btnCheckInOut.text = getString(R.string.see_tomorrow)
                    //Stop Productive Hours
                    stopProductiveService()
                    mBinding.btnCheckInOut.background =
                        ContextCompat.getDrawable(this, R.drawable.curved_button_bg)
                    CoroutineScope(Dispatchers.IO).launch {
                        employeeDatabase.updateCheckOutTime(0, currentTime)
                    }
                }
            } else if (mBinding.btnCheckInOut.text == getString(R.string.see_tomorrow)) {
                exitDialog(true)
            }
        }
        mBinding.ivCamera.setOnClickListener(View.OnClickListener {
            captureImage()
        })

        mBinding.tvApplyBreak.setOnClickListener {
            if (mBinding.tvApplyBreak.text == getString(R.string.apply_break)) {
                if (mBinding.btnCheckInOut.text == getString(R.string.check_out)) {
                    startBreakService()
                    mBinding.tvApplyBreak.text = getString(R.string.end_break)
                    mBinding.tvApplyBreak.background =
                        ContextCompat.getDrawable(this, R.drawable.layout_bg_blue)
                    stopProductiveService()
                } else {
                    Global.showSnackBar(
                        this,
                        mBinding.root,
                        getString(R.string.break_warning)
                    )
                }
            } else if (mBinding.tvApplyBreak.text == getString(R.string.end_break)) {
                stopBreakService()
                mBinding.tvApplyBreak.text = getString(R.string.apply_break)
                mBinding.tvApplyBreak.background =
                    ContextCompat.getDrawable(this, R.drawable.layout_bg_orange)
                startProductiveService()
            }
        }

        val filter = IntentFilter()
        filter.addAction(Constants.PRODUCTIVE_TIME)
        updateProductiveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //UI update here
                val data = intent.getStringExtra("ProductiveTime")
                mBinding.tvProductiveTime.text = data
            }
        }
        registerReceiver(updateProductiveReceiver, filter)

        val breakFilter = IntentFilter()
        breakFilter.addAction(Constants.BREAK_TIME)
        updateBreakReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                //UI update here
                val breakTime = intent.getStringExtra("BreakTime")
                mBinding.tvBreakTime.text = breakTime
            }
        }
        registerReceiver(updateBreakReceiver, breakFilter)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onProviderLocation(providerLocation: ProviderLocation) {
        mProviderLocation = providerLocation
        setMap()
    }

    private fun startProductiveService() {
        val intent = Intent(this, ProductiveTimerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProductiveService() {
        val intent = Intent(this, ProductiveTimerService::class.java)
        stopService(intent)
    }

    private fun startBreakService() {
        val intent = Intent(this, BreakTimerService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopBreakService() {
        val intent = Intent(this, BreakTimerService::class.java)
        stopService(intent)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Add a marker in Sydney and move the camera
        val location = LatLng(
            mProviderLocation?.latitude?.toDouble()!!,
            mProviderLocation?.longitude?.toDouble()!!
        )
        setAddress(
            mProviderLocation?.latitude?.toDouble()!!,
            mProviderLocation?.longitude?.toDouble()!!
        )
        mMap!!.addMarker(MarkerOptions().position(location).title("User"))
        mMap!!.moveCamera(CameraUpdateFactory.newLatLng(location))
        val mapSettings = mMap?.uiSettings
        mapSettings?.isMyLocationButtonEnabled = true
    }

    private fun setMap() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setAddress(lat: Double, lng: Double) {
        var geocodeMatches: List<Address>? = null
        val address: String?

        try {
            geocodeMatches = Geocoder(this).getFromLocation(lat, lng, 1)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (geocodeMatches != null) {
            address = geocodeMatches[0].getAddressLine(0)
            mBinding.tvAddress.text = address
        }
    }

    private fun captureImage() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_REQUEST_CODE
            )
        } else {
            takePhoto()
        }

    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(packageManager) != null) {
            // Create the File where the photo should go
            try {
                photoFile = createImageFile()
                // Continue only if the File was successfully created
                if (photoFile != null) {
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "com.cvt.employee.fileprovider",
                        photoFile!!
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST)
                }
            } catch (ex: Exception) {
                // Error occurred while creating the File
                displayMessage(baseContext, ex.message.toString())
            }
        } else {
            displayMessage(baseContext, "Null")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    //Permission denied
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_DENIED
                    ) {
                        Global.showPermissionAlertToUser(
                            this,
                            getString(R.string.enable_camera_permission)
                        )
                    }
                } else {
                    //Permission granted
                    takePhoto()
                }
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.absolutePath
        return image
    }

    private fun displayMessage(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAPTURE_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val myBitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
            mBinding.ivPhoto.visibility = View.VISIBLE
            mBinding.ivPhoto.setImageBitmap(myBitmap)
        } else {
            mBinding.ivPhoto.visibility = View.GONE
            displayMessage(baseContext, "Request cancelled or something went wrong.")
        }
    }


    class ProductiveTimerService : Service() {
        private var handler: Handler? = null
        private var totalSeconds: Long = 0
        private var isTimerRunning = false
        private val employeeDatabase by lazy { EmployeeDatabase.getDatabase(this).employeeDao() }

        companion object {
            const val CHANNEL_ID = "ServiceChannel"
            const val CHANNEL_NAME = "CVTChannel"
        }

        private var updateTimer: Runnable? = object : Runnable {
            override fun run() {
                totalSeconds++
                val hours = totalSeconds / 3600
                val minutes = totalSeconds % 3600 / 60
                val seconds = totalSeconds % 60
                val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                val local = Intent()
                local.action = Constants.PRODUCTIVE_TIME
                local.putExtra("ProductiveTime", timerText)
                sendBroadcast(local)
                handler?.postDelayed(this, 1000)
            }
        }

        override fun onCreate() {
            handler = Handler(Looper.getMainLooper())
        }

        private fun startTimer() {
            CoroutineScope(Dispatchers.Default).launch {
                val employee = employeeDatabase.getEmployee()
                if (employee != null) {
                    totalSeconds = employee.productiveTime!!
                } else {
                    totalSeconds = 0L
                    /* val employeeData = Employee(0, 0, 0, "", "")
                     employeeDatabase.insertEmployee(employeeData)*/
                }
                if (!isTimerRunning) {
                    updateTimer?.let { handler?.postDelayed(it, 0) }
                    isTimerRunning = true
                }
            }
        }

        private fun pauseTimer() {
            if (isTimerRunning) {
                handler?.removeCallbacks(updateTimer!!)
                isTimerRunning = false
                CoroutineScope(Dispatchers.Default).launch {
                    val employee = employeeDatabase.getEmployee()
                    //Update break time
                    employeeDatabase.updateProductive(employee.Id, totalSeconds)
                }
            }
        }


        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            createNotificationChannel()
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("Service is running.")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .build()
            startForeground(1, notification)
            startTimer()
            return START_STICKY
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            pauseTimer()
        }

        override fun onBind(intent: Intent?): IBinder? {
            TODO("Not yet implemented")
        }
    }

    class BreakTimerService : Service() {
        private var handler: Handler? = null
        private var totalSeconds: Long = 0
        private var isTimerRunning = false
        private val employeeDatabase by lazy { EmployeeDatabase.getDatabase(this).employeeDao() }

        companion object {
            const val BREAK_CHANNEL_ID = "break_channel"
            const val CHANNEL_NAME = "CVTChannel"
        }

        private var updateTimer: Runnable? = object : Runnable {
            override fun run() {
                totalSeconds++
                val hours = totalSeconds / 3600
                val minutes = totalSeconds % 3600 / 60
                val seconds = totalSeconds % 60
                val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                val local = Intent()
                local.action = Constants.BREAK_TIME
                local.putExtra("BreakTime", timerText)
                sendBroadcast(local)
                handler?.postDelayed(this, 1000)
            }
        }

        override fun onCreate() {
            handler = Handler(Looper.getMainLooper())
        }

        private fun startTimer() {
            CoroutineScope(Dispatchers.Default).launch {
                val employee = employeeDatabase.getEmployee()
                if (employee.breakTime != null) {
                    totalSeconds = employee.breakTime
                } else {
                    totalSeconds = 0L
                }
                if (!isTimerRunning) {
                    updateTimer?.let { handler?.postDelayed(it, 0) }
                    isTimerRunning = true
                }
            }
        }

        private fun pauseTimer() {
            if (isTimerRunning) {
                handler?.removeCallbacks(updateTimer!!)
                isTimerRunning = false
                CoroutineScope(Dispatchers.Default).launch {
                    val employee = employeeDatabase.getEmployee()
                    //Update break time
                    employeeDatabase.updateBreak(employee.Id, totalSeconds)
                }
            }
        }


        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            createNotificationChannel()
            val notification: Notification = NotificationCompat.Builder(this, BREAK_CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("Service is running.")
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .build()
            startForeground(2, notification)
            startTimer()
            return START_STICKY
        }

        private fun createNotificationChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val serviceChannel = NotificationChannel(
                    BREAK_CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(serviceChannel)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            pauseTimer()
        }

        override fun onBind(intent: Intent?): IBinder? {
            TODO("Not yet implemented")
        }
    }

    override fun onStart() {
        super.onStart()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(updateProductiveReceiver)
        unregisterReceiver(updateBreakReceiver)
        EventBus.getDefault().unregister(this)
    }

    private fun checkServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onBackPressed() {
        exitDialog(false)
    }

    private fun exitDialog(deleteEmployee: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.dialogTitle)
        builder.setMessage(R.string.dialogMessage)
        builder.setPositiveButton(R.string.exit) { _, _ ->
            if (deleteEmployee) {
                CoroutineScope(Dispatchers.IO).launch {
                    employeeDatabase.deleteEmployee()
                }
            }
            finishAffinity()
        }
        builder.setNegativeButton(R.string.cancel) { dialogInterface, _ ->
            dialogInterface.dismiss()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()
    }
}