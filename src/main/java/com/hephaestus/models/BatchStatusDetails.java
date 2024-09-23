package com.hephaestus.models;

public class BatchStatusDetails {
        private String inputUrl;
        private int successCount;
        private int errorCount;
        private String errorUrl;
    
        // Default constructor
        public BatchStatusDetails() {
        }

        public BatchStatusDetails(String inputUrl, int successCount, int errorCount, String errorUrl) {
            this.inputUrl = inputUrl;
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.errorUrl = errorUrl;
        }
    
        public String getInputUrl() {
            return inputUrl;
        }
    
        public void setInputUrl(String inputUrl) {
            this.inputUrl = inputUrl;
        }
    
        public int getSuccessCount() {
            return successCount;
        }
    
        public void setSuccessCount(int count) {
            this.successCount = count;
        }
    
        public int getErrorCount() {
            return errorCount;
        }
    
        public void setErrorCount(int count) {
            this.errorCount = count;
        }

        public void setErrorUrl(String errorUrl) {
            this.errorUrl = errorUrl;
        }

        public String getErrorUrl() {
            return errorUrl;
        }

        @Override
        public String toString() {
            return "UrlCount{" +
                    "inputUrl='" + inputUrl + '\'' +
                    ", successCount=" + successCount +
                    ", errorCount=" + errorCount +
                    ", errorUrl='" + errorUrl + '\'' +
                    '}';
        }
    }