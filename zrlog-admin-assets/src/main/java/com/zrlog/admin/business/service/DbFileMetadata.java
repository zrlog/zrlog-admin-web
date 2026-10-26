package com.zrlog.admin.business.service;

/** Versioned website.remark document. Payloads remain Base64 in website.value. */
public final class DbFileMetadata {
    public int formatVersion;
    public boolean ready;
    // Null for legacy files and HTTP uploads until SWS exposes the original multipart filename.
    public String originalName;
    public long size;
    public long uploadedAt;
    public String sha256;
    public String batch;
    public int chunkSize;
    public int chunkCount;

    // Persist cleanup ownership before writing/deleting any chunks. These fields also fence stale writers.
    public String pendingBatch;
    public long pendingUntil;
    public String retiredBatch;
    public boolean deleting;
}
