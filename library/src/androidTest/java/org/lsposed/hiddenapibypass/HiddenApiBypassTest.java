package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
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
import java.lang.reflect.InvocationTargetException;
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

    @Test(expected = NoSuchMethodException.class)
    public void BusesNonSdkApiIsHiddenApi() throws NoSuchMethodException {
        ApplicationInfo.class.getMethod("usesNonSdkApi");
    }

    @Test(expected = NoSuchMethodException.class)
    public void CsetHiddenApiExemptionsIsHiddenApi() throws NoSuchMethodException {
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void DnewIActivityManagerDefault() throws NoSuchMethodException, ClassNotFoundException {
        Class.forName("android.app.IActivityManager$Default").getDeclaredConstructor();
    }

    @Test
    public void EinvokeNonSdkApiWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        HiddenApiBypass.invoke(ApplicationInfo.class, new ApplicationInfo(), "usesNonSdkApi");
    }

    @Test
    public void FnewIActivityManagerStubWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, InstantiationException {
        Class<?> stub = Class.forName("android.app.IActivityManager$Default");
        Object instance = HiddenApiBypass.newInstance(stub);
        assertSame(instance.getClass(), stub);
    }

    @Test
    public void GsetHiddenApiExemptions() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
    }

    @Test
    public void HclearHiddenApiExemptions() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        exception.expectMessage(containsString("setHiddenApiExemptions"));
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("L"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
        assertTrue(HiddenApiBypass.clearHiddenApiExemptions());
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void IaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("usesNonSdkApi");
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Ldalvik/system/VMRuntime;"));
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }
}
