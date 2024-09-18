package com.hephaestus;

// import com.azure.core.http.HttpResponse;
// import com.azure.data.tables.TableClient;
// import com.azure.data.tables.TableClientBuilder;
// import com.azure.data.tables.models.TableEntity;
// import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchReference;
// import com.hephaestus.models.FhirImportRequest;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
// import java.net.URL;
// import java.net.http.HttpClient;
// import java.net.http.HttpRequest;
// import java.net.http.HttpResponse.BodyHandlers;

public class FhirImportFunction {
    @FunctionName("ImportBatch")
    public void importBatch(@DurableActivityTrigger(name = "ImportBatch") final BatchReference batchReference,
            final ExecutionContext context) {

        batchReference.BatchStatus = "initiated";

        var logger = context.getLogger();
        Helper.saveBatchReference(batchReference, logger);
        logger.info("Importing batch " + batchReference.BatchId + " to FHIR server.");

        // Build FHIR $import request
        // FhirImportRequest fhirImportRequest = new FhirImportRequest();
        // ObjectMapper mapper = new ObjectMapper();
        // String importRequestJson = mapper.writeValueAsString(fhirImportRequest);
        // // Send FHIR $import request
        // try {
        // URL url = new URL(System.getenv("FhirServerUrl") + "/$import");
        // HttpClient client = HttpClient.newHttpClient();
        // HttpRequest request = HttpRequest.newBuilder()
        // .uri(url.toURI())
        // .header("Content-Type", "application/json")
        // .POST(HttpRequest.BodyPublishers.ofString(importRequestJson))
        // .build();

        // HttpResponse<String> response = client.send(request,
        // BodyHandlers.ofString());

        // String body = response.body();
        // context.getLogger().info("FHIR $import response code: " +
        // response.statusCode());
        // // Grab the status location from the response header
        // String statusLocation =
        // response.headers().firstValue("Content-Location").get();

        // // Save FHIR $import response to table

        // TableClient tableClient = new TableClientBuilder()
        // .connectionString("<your-connection-string>") // or use any of the other
        // authentication methods
        // .tableName(tableName)
        // .buildClient();

        // TableEntity entity = new TableEntity(partitionKey, rowKey)
        // .addProperty("Product", "Marker Set")
        // .addProperty("Price", 5.00)
        // .addProperty("Quantity", 21);

        // tableClient.createEntity(entity);

        // } catch (Exception e) {
        // context.getLogger().severe("Error sending FHIR $import request: " +
        // e.getMessage());
        // }
    }
}
