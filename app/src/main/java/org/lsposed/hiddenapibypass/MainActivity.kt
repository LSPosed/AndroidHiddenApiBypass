package org.lsposed.hiddenapibypass

import android.content.pm.ApplicationInfo
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example of a call to a native method
        findViewById<TextView>(R.id.sample_text).text = if (testByPass()) "success" else "fail"
    }

    private fun canAccessHiddenApi() = runCatching {
        ApplicationInfo::class.java.getMethod("getHiddenApiEnforcementPolicy", *arrayOf())
    }.isSuccess

    private fun testByPass(): Boolean {
        return HiddenApiBypass.javaBypass() && canAccessHiddenApi()
    }

    companion object {
        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
