package org.lsposed.hiddenapibypass

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.RelativeLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = RelativeLayout(this)
        val textView = TextView(this)
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        textView.text = if (testByPass()) "success" else "fail"
        params.addRule(RelativeLayout.CENTER_HORIZONTAL)
        params.addRule(RelativeLayout.CENTER_VERTICAL)
        layout.addView(textView, params)
        setContentView(layout)
    }

    private fun canAccessHiddenApi() = runCatching {
        ApplicationInfo::class.java.getMethod("getHiddenApiEnforcementPolicy", *arrayOf())
    }.isSuccess

    private fun testByPass() =
        HiddenApiBypass.setHiddenApiExemptions("Landroid/") && canAccessHiddenApi()
}
