package com.trackmygymgains

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun applicationDeclaresLauncherIcon() {
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }

    @Test
    fun launcherIconDrawableResolves() {
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
    }
}
