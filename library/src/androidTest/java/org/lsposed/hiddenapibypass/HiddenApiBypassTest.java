package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.ClipDrawable;
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

@SuppressWarnings("JavaReflectionMemberAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4.class)
public class HiddenApiBypassTest {

    private final Class<?> runtime = Class.forName("dalvik.system.VMRuntime");

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public HiddenApiBypassTest() throws ClassNotFoundException {
    }

    @Test
    public void AgetDeclaredMethods() {
        List<Executable> methods = HiddenApiBypass.getDeclaredMethods(runtime);
        Optional<Executable> getRuntime = methods.stream().filter(it -> it.getName().equals("getRuntime")).findFirst();
        assertTrue(getRuntime.isPresent());
        Optional<Executable> setHiddenApiExemptions = methods.stream().filter(it -> it.getName().equals("setHiddenApiExemptions")).findFirst();
        assertTrue(setHiddenApiExemptions.isPresent());
    }

    @Test(expected = NoSuchMethodException.class)
    public void BusesNonSdkApiIsHiddenApi() throws NoSuchMethodException {
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
    }

    @Test(expected = NoSuchMethodException.class)
    public void CsetHiddenApiExemptionsIsHiddenApi() throws NoSuchMethodException {
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void DnewClipDrawableIsHiddenApi() throws NoSuchMethodException {
        ClipDrawable.class.getDeclaredConstructor();
    }

    @Test(expected = NoSuchFieldException.class)
    public void ElongVersionCodeIsHiddenApi() throws NoSuchFieldException {
        ApplicationInfo.class.getDeclaredField("longVersionCode");
    }

    @Test(expected = NoSuchFieldException.class)
    public void FHiddenApiEnforcementDefaultIsHiddenApi() throws NoSuchFieldException {
        ApplicationInfo.class.getDeclaredField("HIDDEN_API_ENFORCEMENT_DEFAULT");
    }

    @Test
    public void GtestGetInstanceFields() {
        assertTrue(HiddenApiBypass.getInstanceFields(ApplicationInfo.class).stream().anyMatch(i -> i.getName().equals("longVersionCode")));
    }

    @Test
    public void HtestGetStaticFields() {
        assertTrue(HiddenApiBypass.getStaticFields(ApplicationInfo.class).stream().anyMatch(i -> i.getName().equals("HIDDEN_API_ENFORCEMENT_DEFAULT")));
    }

    @Test
    public void IinvokeNonSdkApiWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        assertNotEquals(HiddenApiBypass.getDeclaredMethod(ApplicationInfo.class, "getHiddenApiEnforcementPolicy"), null);
        HiddenApiBypass.invoke(ApplicationInfo.class, new ApplicationInfo(), "getHiddenApiEnforcementPolicy");
    }

    @Test
    public void JnewClipDrawableWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        assertNotEquals(HiddenApiBypass.getDeclaredConstructor(ClipDrawable.class), null);
        Object instance = HiddenApiBypass.newInstance(ClipDrawable.class);
        assertSame(instance.getClass(), ClipDrawable.class);
    }

    @Test
    public void KgetAllMethodsWithoutExemption() {
        assertTrue(HiddenApiBypass.getDeclaredMethods(ApplicationInfo.class).stream().anyMatch(e -> e.getName().equals("getHiddenApiEnforcementPolicy")));
    }

    @Test
    public void LsetHiddenApiExemptions() throws NoSuchMethodException, NoSuchFieldException {
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        ApplicationInfo.class.getDeclaredField("longVersionCode");
        ApplicationInfo.class.getDeclaredField("HIDDEN_API_ENFORCEMENT_DEFAULT");
    }

    @Test
    public void MclearHiddenApiExemptions() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        exception.expectMessage(containsString("setHiddenApiExemptions"));
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("L"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.clearHiddenApiExemptions());
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void NaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Ldalvik/system/VMRuntime;"));
        runtime.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void OtestCheckArgsForInvokeMethod() {
        class X {
        }
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{}, new Object[]{new Object()}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1.0}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{null}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{1}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{null}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Object.class}, new Object[]{new X()}));
        assertFalse(Helper.checkArgsForInvokeMethod(new Class[]{X.class}, new Object[]{new Object()}));
        assertTrue(Helper.checkArgsForInvokeMethod(new Class[]{Object.class, int.class, byte.class, short.class, char.class, double.class, float.class, boolean.class, long.class}, new Object[]{new X(), 1, (byte) 0, (short) 2, 'c', 1.1, 1.2f, false, 114514L}));
    }

}
