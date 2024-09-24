package com.hephaestus.models;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.ArrayList;

/*

Sample payload

{
    "error": [
        {
            "count": 2,
            "inputUrl": "https://st3swm6va3d5km6.blob.core.windows.net/data/2024-07-22-part2.ndjson",
            "type": null,
            "url": "https://st3swm6va3d5km6.blob.core.windows.net/fhirlogs/1_3.ndjson"
        }
    ],
    "output": [
        {
            "count": 74,
            "inputUrl": "https://st3swm6va3d5km6.blob.core.windows.net/data/2024-07-22-part1.ndjson",
            "type": null
        },
        {
            "count": 64,
            "inputUrl": "https://st3swm6va3d5km6.blob.core.windows.net/data/2024-07-22-part2.ndjson",
            "type": null
        }
    ],
    "request": "https://ahds3swm6va3d5km6-api1.fhir.azurehealthcareapis.com/$import",
    "transactionTime": "2024-09-17T17:34:52.347+00:00"
}

 */

// perform all aggregation of results and errors in getAllResults method as well as aggregate counts
// in a single loop to improve performance
public class BatchStatusResponse implements Serializable {
    private String transactionTime;
    private String request;
    private List<Output> output;
    private List<Error> error;

    public String getTransactionTime() {
        return transactionTime;
    }

    public void setTransactionTime(String transactionTime) {
        this.transactionTime = transactionTime;
    }

    public String getRequest() {
        return request;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public List<Output> getOutput() {
        return output;
    }

    public void setOutput(List<Output> output) {
        this.output = output;
    }

    public List<Error> getError() {
        return error;
    }

    public List<Pair<Output, Error>> getAllResults() {
        List<Pair<Output, Error>> results = new ArrayList<Pair<Output, Error>>();

        for (var output : this.output) {
            results.add(Pair.of(output, null));
        }

        for (var error : this.error) {
            Pair<Output, Error> result = results.stream()
                    .filter(res -> res.getRight() == null && res.getLeft() != null
                            && res.getLeft().getInputUrl().equals(error.inputUrl))
                    .findFirst()
                    .orElse(null);

            if (result == null) {
                results.add(Pair.of(null, error));
            } else {
                result.setValue(error);
            }
        }

        return results;
    }

    public void setError(List<Error> error) {
        this.error = error;
    }

    public static class Output implements Serializable {
        private String type;
        private int count;
        private String inputUrl;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getInputUrl() {
            return inputUrl;
        }

        public void setInputUrl(String inputUrl) {
            this.inputUrl = inputUrl;
        }
    }

    public static class Error implements Serializable {
        private String type;
        private int count;
        private String inputUrl;
        private String url;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String getInputUrl() {
            return inputUrl;
        }

        public void setInputUrl(String inputUrl) {
            this.inputUrl = inputUrl;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
