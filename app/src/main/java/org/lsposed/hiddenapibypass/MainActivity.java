package org.lsposed.hiddenapibypass;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RelativeLayout layout = new RelativeLayout(this);
        TextView textView = new TextView(this);
        textView.setText(testByPass() ? "success" : "fail");
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_HORIZONTAL);
        params.addRule(RelativeLayout.CENTER_VERTICAL);
        layout.addView(textView, params);
        setContentView(layout);
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private boolean canAccessHiddenApi() {
        try {
            //noinspection JavaReflectionMemberAccess
            ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        } catch (NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    private boolean testByPass() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return true;
        return HiddenApiBypass.setHiddenApiExemptions("Landroid/") && canAccessHiddenApi();
    }
}
