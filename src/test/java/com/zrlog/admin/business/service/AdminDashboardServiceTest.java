package com.zrlog.admin.business.service;

import com.zrlog.admin.business.rest.response.AdminDashboardCardResponse;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class AdminDashboardServiceTest {

    @Test
    public void shouldFillSurfacePathsFromSafePluginName() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setPluginName(" reminder ");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("reminder", normalizedPanel.getId());
        assertEquals("reminder", normalizedPanel.getPluginName());
        assertEquals("/admin/plugins/reminder/surface", normalizedPanel.getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", normalizedPanel.getActionUrl());
    }

    @Test
    public void shouldKeepAdvancedInternalSurfacePaths() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setId("custom-panel");
        panel.setSurfaceUrl(" /admin/plugins/reminder/surface?scope=dashboard ");
        panel.setActionUrl("/admin/plugins/reminder/surfaceAction");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("/admin/plugins/reminder/surface?scope=dashboard", normalizedPanel.getSurfaceUrl());
        assertEquals("/admin/plugins/reminder/surfaceAction", normalizedPanel.getActionUrl());
    }

    @Test
    public void shouldDropExternalSurfaceUrls() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setId("external-panel");
        panel.setSurfaceUrl("https://example.com/surface");
        panel.setActionUrl("//example.com/surfaceAction");

        assertNull(AdminDashboardService.normalizePanel(panel));
    }

    @Test
    public void shouldDropPluginNameContainingPathSeparators() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("surface");
        panel.setPluginName("../reminder");

        assertNull(AdminDashboardService.normalizePanel(panel));
    }

    @Test
    public void shouldNormalizeInternalPluginViewPath() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setPluginName("reminder");
        panel.setViewUrl(" /settings/index?tab=base ");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("settings/index?tab=base", normalizedPanel.getViewUrl());
    }

    @Test
    public void shouldKeepExternalHttpPluginViewUrl() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setViewUrl("https://example.com/dashboard");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("https://example.com/dashboard", normalizedPanel.getViewUrl());
    }

    @Test
    public void shouldDefaultUnsafePluginViewPath() {
        AdminDashboardCardResponse panel = new AdminDashboardCardResponse();
        panel.setType("view");
        panel.setPluginName("reminder");
        panel.setViewUrl("../admin");

        AdminDashboardCardResponse normalizedPanel = AdminDashboardService.normalizePanel(panel);

        assertSame(panel, normalizedPanel);
        assertEquals("index", normalizedPanel.getViewUrl());
    }
}
