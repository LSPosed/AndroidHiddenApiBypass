package org.lsposed.hiddenapibypass;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import dalvik.system.VMRuntime;


class S {
    static int x = 114514;
}

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

    @Test
    public void KtestCheckArgsForInvokeMethod() {
        class X {
        }
        assertFalse(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{}, new Object[]{new Object()}));
        assertTrue(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1}));
        assertFalse(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{1.0}));
        assertFalse(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{int.class}, new Object[]{null}));
        assertTrue(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{1}));
        assertTrue(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{Integer.class}, new Object[]{null}));
        assertTrue(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{Object.class}, new Object[]{new X()}));
        assertFalse(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{X.class}, new Object[]{new Object()}));
        assertTrue(HiddenApiBypass.checkArgsForInvokeMethod(new Class[]{Object.class, int.class, byte.class, short.class, char.class, double.class, float.class, boolean.class, long.class}, new Object[]{new X(), 1, (byte) 0, (short) 2, 'c', 1.1, 1.2f, false, 114514L}));
    }

    @Test
    public void LtestGetInstanceFields() throws IllegalAccessException {
        class X {
            final int x = 114514;
        }
        X x = new X();
        assertEquals((int)HiddenApiBypass.getInstanceFields(X.class).get(1).getInt(x), 114514);
    }

    @Test
    public void LtestGetStaticFields() throws IllegalAccessException {
        assertEquals((int)HiddenApiBypass.getStaticFields(S.class).get(0).getInt(null), 114514);
    }
}
