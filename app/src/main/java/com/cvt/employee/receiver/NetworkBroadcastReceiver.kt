package com.cvt.employee.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cvt.employee.common.Constants
import com.cvt.employee.common.Global
import com.cvt.employee.common.NetworkListenerModel
import org.greenrobot.eventbus.EventBus

class NetworkBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val networkListenerModel = NetworkListenerModel()
        networkListenerModel.isOnline = Global.isConnectedToNetwork(context)
        networkListenerModel.type = Constants.NETWORK_CHANGE
        EventBus.getDefault().post(networkListenerModel)
    }
}