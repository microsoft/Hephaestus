package com.hephaestus;

import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.FhirImportRequest;
import com.hephaestus.models.NdJsonReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

public class FhirImportFunction {
    @FunctionName("ImportBatch")
    public void importBatch(@DurableActivityTrigger(name = "ImportBatch") final BatchReference batchReference,
            final ExecutionContext context) {

        batchReference.BatchStatus = "initiated";

        var logger = context.getLogger();
        Helper.saveBatchReference(batchReference, logger);
        logger.log(Level.INFO, "Importing batch {0} to FHIR server.", batchReference.BatchId);

        // Build FHIR $import request
        FhirImportRequest fhirImportRequest = new FhirImportRequest();
        fhirImportRequest.setResourceType("Parameters");

        List<FhirImportRequest.Parameter> parameters = new ArrayList<>();
        
        // inputFormat parameter
        FhirImportRequest.Parameter inputFormatParam = new FhirImportRequest.Parameter();
        inputFormatParam.setName("inputFormat");
        inputFormatParam.setValueString("application/fhir+ndjson");
        parameters.add(inputFormatParam);

        // mode parameter
        FhirImportRequest.Parameter modeParam = new FhirImportRequest.Parameter();
        modeParam.setName("mode");
        modeParam.setValueString(System.getenv("FHIR_IMPORT_MODE"));
        parameters.add(modeParam);

        // Get the blob container URL
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr")).buildClient();
        String blobBaseUrl = blobServiceClient.getAccountUrl() + "/" + System.getenv("FHIR_STORAGE_CONTAINER");
        
        for (NdJsonReference file : batchReference.Files) {
            // input parameter
            FhirImportRequest.Parameter inputParam = new FhirImportRequest.Parameter();
            inputParam.setName("input");
            FhirImportRequest.Parameter.Part urlPart = new FhirImportRequest.Parameter.Part();
            urlPart.setName("url");
            urlPart.setValueUri(blobBaseUrl + "/" + file.filename);
            inputParam.setPart(new FhirImportRequest.Parameter.Part[] { urlPart });
            parameters.add(inputParam);
        }

        fhirImportRequest.setParameter(parameters.toArray(new FhirImportRequest.Parameter[0]));
        ObjectMapper mapper = new ObjectMapper();
        
        // Send FHIR $import request
        try {
            String importRequestJson = mapper.writeValueAsString(fhirImportRequest);
            URL url = new URL(System.getenv("FHIR_SERVER_URL") + "/$import");
            HttpClient client = HttpClient.newHttpClient();
            TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
            TokenRequestContext tokenRequestContext = new TokenRequestContext();
            tokenRequestContext.addScopes(System.getenv("FHIR_SERVER_URL") + "/.default");
            AccessToken authToken = tokenCredential.getTokenSync(tokenRequestContext);
            HttpRequest request = HttpRequest.newBuilder()
            .uri(url.toURI())
            .header("Authorization", "Bearer " + authToken.getToken())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(importRequestJson))
            .build();

            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());

            context.getLogger().log(Level.INFO, "FHIR $import response code: {0}", response.statusCode());
            // Grab the status location from the response header
            String statusLocation = response.headers().firstValue("Content-Location").get();

            // Save FHIR $import response to table
            batchReference.BatchStatusUrl = statusLocation;
            Helper.saveBatchReference(batchReference, logger);
        } catch (Exception e) {
            context.getLogger().log(Level.SEVERE, "Error sending FHIR $import request: {0}", e.getMessage());
        }
    }
}
