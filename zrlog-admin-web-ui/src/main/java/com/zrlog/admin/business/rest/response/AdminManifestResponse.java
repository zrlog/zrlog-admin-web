package com.zrlog.admin.business.rest.response;

import java.util.ArrayList;
import java.util.List;

public class AdminManifestResponse {

    private String short_name;
    private String name;
    private String start_url;
    private List<Icon> icons = new ArrayList<>();
    private String display;
    private String theme_color;
    private String background_color;
    private String description;
    private String id;

    public String getShort_name() {
        return short_name;
    }

    public void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStart_url() {
        return start_url;
    }

    public void setStart_url(String start_url) {
        this.start_url = start_url;
    }

    public List<Icon> getIcons() {
        return icons;
    }

    public void setIcons(List<Icon> icons) {
        this.icons = icons;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public String getTheme_color() {
        return theme_color;
    }

    public void setTheme_color(String theme_color) {
        this.theme_color = theme_color;
    }

    public String getBackground_color() {
        return background_color;
    }

    public void setBackground_color(String background_color) {
        this.background_color = background_color;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public static class Icon {

        private String src;
        private String sizes;
        private String type;
        private String purpose;

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }

        public String getSizes() {
            return sizes;
        }

        public void setSizes(String sizes) {
            this.sizes = sizes;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getPurpose() {
            return purpose;
        }

        public void setPurpose(String purpose) {
            this.purpose = purpose;
        }
    }
}
