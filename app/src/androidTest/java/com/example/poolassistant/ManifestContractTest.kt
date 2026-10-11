package com.example.poolassistant

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.example.poolassistant.overlay.AssistantService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestContractTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val packageInfo = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_PERMISSIONS
    )

    @Test
    fun declaresOverlayAndForegroundServicePermissions() {
        val permissions = packageInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(Manifest.permission.SYSTEM_ALERT_WINDOW in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
    }

    @Test
    @SdkSuppress(minSdkVersion = 29)
    fun assistantServiceIsMediaProjectionForegroundService() {
        val serviceInfo = context.packageManager.getServiceInfo(
            ComponentName(context, AssistantService::class.java),
            PackageManager.GET_META_DATA
        )

        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }
}
