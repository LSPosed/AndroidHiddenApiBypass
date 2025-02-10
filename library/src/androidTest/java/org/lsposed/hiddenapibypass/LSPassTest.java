package org.lsposed.hiddenapibypass;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.ActivityOptions;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.reflect.InvocationTargetException;

@SuppressWarnings("JavaReflectionMemberAccess")
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
@RunWith(AndroidJUnit4.class)
public class LSPassTest {

    @Test(expected = NoSuchMethodException.class)
    public void AnewActivityOptionsIsHiddenApi() throws NoSuchMethodException {
        ActivityOptions.class.getDeclaredConstructor();
    }

    @Test(expected = NoSuchMethodException.class)
    public void BgetHeightIsHiddenApi() throws NoSuchMethodException {
        ActivityOptions.class.getDeclaredMethod("getHeight");
    }

    @Test(expected = NoSuchFieldException.class)
    public void CmHeightIsHiddenApi() throws NoSuchFieldException {
        ActivityOptions.class.getDeclaredField("mHeight");
    }

    @Test
    public void DgetDeclaredFieldsTest() {
        var fields = LSPass.getDeclaredFields(ActivityOptions.class);
        assertTrue(fields.stream().anyMatch(i -> i.getName().equals("mHeight")));
        var instance = LSPass.getInstanceFields(ActivityOptions.class);
        assertTrue(instance.stream().anyMatch(i -> i.getName().equals("mHeight")));
        var staticFields = LSPass.getStaticFields(ActivityOptions.class);
        assertTrue(staticFields.stream().anyMatch(i -> i.getName().equals("ANIM_NONE")));
        assertEquals(fields.size(), instance.size() + staticFields.size());
    }

    @Test
    public void EgetDeclaredMethodTest() throws NoSuchMethodException {
        assertNotNull(LSPass.getDeclaredMethod(ActivityOptions.class, "getHeight"));
    }

    @Test
    public void FnewActivityOptionsWithoutExemption() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, InstantiationException {
        assertNotEquals(null, LSPass.getDeclaredConstructor(ActivityOptions.class));
        Object instance = LSPass.newInstance(ActivityOptions.class);
        assertSame(ActivityOptions.class, instance.getClass());
    }

    @Test(expected = NoSuchFieldException.class)
    public void GclearHiddenApiExemptionsTest() throws NoSuchFieldException {
        assertTrue(LSPass.addHiddenApiExemptions("L"));
        assertTrue(LSPass.clearHiddenApiExemptions());
        ActivityOptions.class.getDeclaredField("mHeight");
    }

    @Test
    public void HaddHiddenApiExemptionsTest() throws NoSuchMethodException {
        assertTrue(LSPass.addHiddenApiExemptions("L"));
        assertTrue(LSPass.addHiddenApiExemptions("xx"));
        ActivityOptions.class.getDeclaredMethod("getHeight");
    }
}
