package com.zrlog.admin.web.interceptor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminPluginInterceptorTest {

    @Test
    public void shouldTreatOnlyPluginRootPwaResourcesAsPublic() {
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.webmanifest"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.json"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-icon"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-sw.js"));
        assertTrue(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/manifest.webmanifest?v=1"));

        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/static/app.js"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/api/status"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/nested/manifest.json"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugins/reminder/pwa-icon/"));
        assertFalse(AdminPluginInterceptor.isPluginPwaResource("/admin/plugin/reminder/manifest.json"));
    }

    @Test
    public void shouldConvertAdminPluginPathToPluginCorePath() {
        assertEquals("/reminder/manifest.webmanifest",
                AdminPluginInterceptor.pluginCoreUri("/admin/plugins/reminder/manifest.webmanifest"));
    }
}
