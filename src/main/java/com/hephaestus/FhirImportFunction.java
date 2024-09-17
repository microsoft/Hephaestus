package com.hephaestus;

import com.azure.core.http.HttpResponse;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.FhirImportRequest;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;

public class FhirImportFunction {
    @FunctionName("ImportBatch")
    public void importBatch(@DurableActivityTrigger(name = "batchReference") final BatchReference batchReference,
            final ExecutionContext context) 
            {
                // Build FHIR $import request
                FhirImportRequest fhirImportRequest = new FhirImportRequest();
                // Send FHIR $import request
                try {
                    URL url = new URL("https://fhir-server-url/$import");
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(url.toURI())
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(fhirImportRequest.toJson()))
                            .build();

                    HttpResponse<String> response = client.send(request, BodyHandlers.ofString());    

                    String body = response.body();
                    context.getLogger().info("FHIR $import response code: " + response.statusCode());
                    // Grab the status location from the response header
                    String statusLocation = response.headers().firstValue("Content-Location").get();

                } catch (Exception e) {
                    context.getLogger().severe("Error sending FHIR $import request: " + e.getMessage());
                }

                // Save FHIR $import response to table
            }
}
