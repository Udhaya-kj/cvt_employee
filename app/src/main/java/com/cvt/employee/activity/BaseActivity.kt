package com.cvt.employee.activity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import androidx.annotation.Nullable
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.cvt.employee.R
import com.cvt.employee.bottomsheetfragment.NetworkStatusDialog
import com.cvt.employee.common.Constants
import com.cvt.employee.common.GPSChangeEvent
import com.cvt.employee.common.Global
import com.cvt.employee.common.NetworkListenerModel
import com.cvt.employee.common.NetworkRefresh
import com.cvt.employee.common.ProviderLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

open class BaseActivity : FragmentActivity() {
    private var networkDialog: NetworkStatusDialog? = null

    companion object {
        const val REQUEST_CODE_CHECK_SETTINGS = 100
        const val LOCATION_REQUEST_CODE = 101
    }

    private var mLocationCallback: LocationCallback? = null
    private var mLastLocation: Location? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private lateinit var mLocationPermission: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationPermission = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun requestLocationCallback() {
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this@BaseActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_DENIED
        ) {
            updateUI()
        } else {
            requestPermissions(mLocationPermission, LOCATION_REQUEST_CODE)
        }
    }

    private fun updateUI() {
        if (Global.isGPSEnabled(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            mFusedLocationClient?.lastLocation?.addOnSuccessListener {
                // Got last known location. In some rare situations this can be null.
                if (it != null) {
                    updateLocation(it)
                } else {
                    enableLocationSettings()
                }
            }
        } else {
            enableLocationSettings()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    //Permission denied
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_DENIED
                    ) {
                        Global.showPermissionAlertToUser(
                            this@BaseActivity,
                            getString(R.string.enable_location_permission)
                        )
                    }
                } else {
                    //Permission granted
                    updateUI()
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        requestLocationPermission()
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onGPSCheckEvent(gpsChangeEvent: GPSChangeEvent) {
        if (!gpsChangeEvent.isGpsEnabled) {
            enableLocationSettings()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onNetworkListen(networkListenerModel: NetworkListenerModel) {
        val type = networkListenerModel.type
        if (type == Constants.NETWORK_CHANGE) {
            val isOnline: Boolean = networkListenerModel.isOnline
            if (!isOnline) {
                if (networkDialog == null) {
                    networkDialog = NetworkStatusDialog()
                    networkDialog!!.isCancelable = false
                    networkDialog!!.show(supportFragmentManager, Constants.NETWORK)
                }
            } else {
                val dialog = supportFragmentManager.findFragmentByTag(Constants.NETWORK)
                if (dialog != null) {
                    val ft = supportFragmentManager.beginTransaction()
                    ft.remove(dialog)
                    ft.commit()
                    networkDialog = null
                    EventBus.getDefault().post(NetworkRefresh())
                }
            }
        }
    }

    private fun enableLocationSettings() {
        val locationRequest: LocationRequest = LocationRequest.create()
            .apply {
                priority = Priority.PRIORITY_HIGH_ACCURACY
                interval = 30 * 1000
                fastestInterval = 100
            }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        LocationServices
            .getSettingsClient(this)
            .checkLocationSettings(builder.build())
            .addOnSuccessListener(this) {
                getLastLocation(locationRequest)
            }
            .addOnFailureListener(this) { ex ->
                if (ex is ResolvableApiException) {
                    // Location settings are NOT satisfied,  but this can be fixed  by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),  and check the result in onActivityResult().
                        ex.startResolutionForResult(
                            this,
                            REQUEST_CODE_CHECK_SETTINGS
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        @Nullable data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (REQUEST_CODE_CHECK_SETTINGS == requestCode) {
            if (Activity.RESULT_OK == resultCode) {
                //User clicked "OK" button from dialog
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                mFusedLocationClient?.lastLocation?.addOnSuccessListener {
                    // Got last known location. In some rare situations this can be null.
                    if (it != null) {
                        updateLocation(it)
                    } else {
                        enableLocationSettings()
                    }
                }
            } else {
                //User clicked "No" button from dialog
                finishAffinity()
            }
        }
    }

    private fun getLastLocation(locationRequest: LocationRequest) {
        try {
            mFusedLocationClient?.lastLocation?.addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    updateLocation(task.result)
                } else {
                    setLocationRequestCallBack()?.let {
                        mFusedLocationClient?.requestLocationUpdates(
                            locationRequest,
                            it, Looper.myLooper()
                        )
                    }
                }
            }

        } catch (unlikely: SecurityException) {
            Global.setLogger("TAG", "Lost location permission.$unlikely")
        }
    }

    private fun setLocationRequestCallBack(): LocationCallback? {
        if (mLocationCallback == null) {
            mLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    if (mLastLocation == null) {
                        val mLastLocation = locationResult.lastLocation
                        mLastLocation?.let { updateLocation(it) }
                    }
                    mFusedLocationClient?.removeLocationUpdates(this)
                }
            }
        }
        return mLocationCallback
    }

    private fun updateLocation(lastLocation: Location) {
        //send LatLong to activities
        val providerLocation = ProviderLocation()
        providerLocation.latitude = lastLocation.latitude.toString()
        providerLocation.longitude = lastLocation.longitude.toString()
        EventBus.getDefault().post(providerLocation)
    }
}