package com.zrlog.admin.business.rest.response;

public class ReplaceArticleResourceUrlResponse {

    private int scannedArticles;
    private int updatedArticles;
    private int updatedFields;

    public int getScannedArticles() {
        return scannedArticles;
    }

    public void setScannedArticles(int scannedArticles) {
        this.scannedArticles = scannedArticles;
    }

    public int getUpdatedArticles() {
        return updatedArticles;
    }

    public void setUpdatedArticles(int updatedArticles) {
        this.updatedArticles = updatedArticles;
    }

    public int getUpdatedFields() {
        return updatedFields;
    }

    public void setUpdatedFields(int updatedFields) {
        this.updatedFields = updatedFields;
    }
}
