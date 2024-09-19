package com.hephaestus.models;

public class ImportProcessedQueueMessage {
    private String rowKey;
    private String statusUrl;

    // Default constructor
    public ImportProcessedQueueMessage() {
    }

    public ImportProcessedQueueMessage(String rowKey, String statusUrl) {
        this.rowKey = rowKey;
        this.statusUrl = statusUrl;
    }

    public String getRowKey() {
        return rowKey;
    }

    public void setRowKey(String rowKey) {
        this.rowKey = rowKey;
    }

    public String getStatusUrl() {
        return statusUrl;
    }

    public void setStatusUrl(String statusUrl) {
        this.statusUrl = statusUrl;
    }
    
}
