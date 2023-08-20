package com.cvt.employee.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.util.Log
import com.cvt.employee.common.GPSChangeEvent
import org.greenrobot.eventbus.EventBus

class GPSReceiver : BroadcastReceiver() {
    companion object {
        private var IS_FIRST_TIME_GPS_ENABLED = false
        private var IS_FIRST_TIME_GPS_DISABLED = false
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("TAG", "onReceive---: "+intent?.action)
        if (intent?.action!!.matches(LocationManager.PROVIDERS_CHANGED_ACTION.toRegex())) {
            val locationManager =
                context?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (!IS_FIRST_TIME_GPS_ENABLED) {
                    val gpsChangeEvent = GPSChangeEvent()
                    gpsChangeEvent.isGpsEnabled = true
                    EventBus.getDefault().post(gpsChangeEvent)
                    Log.d("TAG", "onReceive---: gps connected")
                    IS_FIRST_TIME_GPS_ENABLED = true
                }
                IS_FIRST_TIME_GPS_DISABLED = false

            } else {
                if (!IS_FIRST_TIME_GPS_DISABLED) {
                    val gpsChangeEvent = GPSChangeEvent()
                    gpsChangeEvent.isGpsEnabled = false
                    EventBus.getDefault().post(gpsChangeEvent)
                    Log.d("TAG", "onReceive---: gps not connected")
                    IS_FIRST_TIME_GPS_DISABLED = true
                }
                IS_FIRST_TIME_GPS_ENABLED = false

            }
        }
    }
}