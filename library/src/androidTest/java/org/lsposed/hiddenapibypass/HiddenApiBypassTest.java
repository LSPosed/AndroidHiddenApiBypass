package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.pm.ApplicationInfo;
import android.graphics.drawable.ClipDrawable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

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
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
    }

    @Test(expected = NoSuchMethodException.class)
    public void CsetHiddenApiExemptionsIsHiddenApi() throws NoSuchMethodException {
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test(expected = NoSuchMethodException.class)
    public void DnewClipDrawable() throws NoSuchMethodException {
        ClipDrawable.class.getDeclaredConstructor();
    }

    @Test
    public void EinvokeNonSdkApiWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        HiddenApiBypass.invoke(ApplicationInfo.class, new ApplicationInfo(), "getHiddenApiEnforcementPolicy");
    }

    @Test
    public void FnewClipDrawableWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        Object instance = HiddenApiBypass.newInstance(ClipDrawable.class);
        assertSame(instance.getClass(), ClipDrawable.class);
    }

    @Test
    public void GgetAllMethodsWithoutExemption() {
        assertTrue(HiddenApiBypass.getDeclaredMethods(ApplicationInfo.class).stream().anyMatch(e -> e.getName().equals("getHiddenApiEnforcementPolicy")));
    }

    @Test
    public void HsetHiddenApiExemptions() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
    }

    @Test
    public void IclearHiddenApiExemptions() throws NoSuchMethodException {
        exception.expect(NoSuchMethodException.class);
        exception.expectMessage(containsString("setHiddenApiExemptions"));
        assertTrue(HiddenApiBypass.setHiddenApiExemptions("L"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.clearHiddenApiExemptions());
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }

    @Test
    public void JaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;"));
        ApplicationInfo.class.getMethod("getHiddenApiEnforcementPolicy");
        assertTrue(HiddenApiBypass.addHiddenApiExemptions("Ldalvik/system/VMRuntime;"));
        VMRuntime.class.getMethod("setHiddenApiExemptions", String[].class);
    }
}
