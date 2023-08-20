package com.cvt.employee.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.cvt.employee.R
import com.google.android.material.snackbar.Snackbar
import org.greenrobot.eventbus.android.BuildConfig

object Global {

    fun checkCameraPermission(context: Context): Boolean {
        val result = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val result1 = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        return result && result1
    }

    fun checkStoragePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == (PackageManager.PERMISSION_GRANTED)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun checkNotificationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == (PackageManager.PERMISSION_GRANTED)
    }

    fun checkLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == (PackageManager.PERMISSION_GRANTED)
    }

    fun isConnectedToNetwork(context: Context?): Boolean {
        val cm = context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    fun preventDoubleClick(view: View?) {
        view?.isEnabled = false
        view?.postDelayed(Runnable {
            view.isEnabled = true
        }, 1000)
    }

    fun setLogger(tag: String?, msg: String?) {
        if (BuildConfig.DEBUG) {
            if (msg != null) {
                Log.e(tag, msg)
            }
        }
    }

    fun longToast(context: Context?, message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun shortToast(context: Context?, message: String?) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun showSnackBar(context: Context, view: View, message: String) {
        val snackBar: Snackbar = Snackbar
            .make(view, message, Snackbar.LENGTH_LONG)
        snackBar.setBackgroundTint(ContextCompat.getColor(context, R.color.black))
        snackBar.show()
    }

    fun isGPSEnabled(mContext: Context): Boolean {
        val locationManager = mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    fun showPermissionAlertToUser(context: Context, message: String) {
        val alertDialogBuilder = AlertDialog.Builder(context)
        alertDialogBuilder.setMessage(message)
            .setCancelable(false)
            // .setTitle("Goto Settings Page To Enable GPS")
            .setPositiveButton(context.getString(R.string.text_ok)) { dialog, id ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
                context.startActivity(intent)
                dialog.dismiss()
            }
        val alert = alertDialogBuilder.create()
        alert.show()
    }

}