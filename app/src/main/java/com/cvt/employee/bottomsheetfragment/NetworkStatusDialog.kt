package com.cvt.employee.bottomsheetfragment

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import com.cvt.employee.R
import com.cvt.employee.common.Global
import com.cvt.employee.databinding.LayoutDialogNetworkBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class NetworkStatusDialog : BottomSheetDialogFragment(), OnClickListener {
    private var mActivity: Activity? = null
    private var mBinding: LayoutDialogNetworkBinding? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mBinding = LayoutDialogNetworkBinding.inflate(inflater, container, false)
        mActivity = activity
        mBinding?.tvOk?.setOnClickListener(this)
        return mBinding?.root!!
    }

    override fun onClick(view: View?) {
        Global.preventDoubleClick(view)
        if (view?.id == R.id.tv_ok) {
            if (Global.isConnectedToNetwork(mActivity)) {
                dialog?.dismiss()
            } else {
                Global.shortToast(
                    mActivity,
                    mActivity?.getString(R.string.check_your_network_connection)
                )
            }
        }
    }
}