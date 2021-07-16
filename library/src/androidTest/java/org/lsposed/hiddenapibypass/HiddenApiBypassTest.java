package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.reflect.Executable;
import java.util.List;
import java.util.Optional;

import dalvik.system.VMRuntime;

@SuppressWarnings("JavaReflectionMemberAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4.class)
public class HiddenApiBypassTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void AgetDeclaredMethods() {
        List<Executable> methods = HiddenApiBypass.getDeclaredMethods(VMRuntime.class);
        Optional<Executable> getRuntime = methods.stream().filter(it -> it.getName().equals("getRuntime")).findFirst();
        assertTrue(getRuntime.isPresent());
        Optional<Executable> setHiddenApiExemptions = methods.stream().filter(it -> it.getName().equals("setHiddenApiExemptions")).findFirst();
        assertTrue(setHiddenApiExemptions.isPresent());
    }

    @Test
    public void BusesNonSdkApiIsHiddenApi() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        ApplicationInfo.class.getMethod("usesNonSdkApi");
    }

    @Test
    public void CsetHiddenApiExemptionsIsHiddenApi() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void DsetHiddenApiExemptions() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
    }

    @Test
    public void EclearHiddenApiExemptions() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        exception.expectMessage(containsString("setHiddenApiExemptions"));
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("L"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
        assertTrue(HiddenApiBypass.clearHiddenApiExemptions());
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void FaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Ldalvik/system/VMRuntime;"));
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }
}
