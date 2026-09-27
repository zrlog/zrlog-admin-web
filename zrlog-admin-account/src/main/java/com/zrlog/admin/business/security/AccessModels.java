package com.zrlog.admin.business.security;

import java.util.List;

public final class AccessModels {
    private AccessModels() { }
    public static class Action {
        public String id;
        public String scope;
        public List<String> roles;
        public List<String> routes;
        public List<Route> routeDetails;
    }
    public static class Route {
        public String path;
        public String descriptionKey;
    }
    public static class Page {
        public String currentRole;
        public List<String> roles;
        public List<Action> actions;
    }
}
