package com.hephaestus;

import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.lang3.tuple.Pair;

import com.microsoft.azure.functions.annotation.*;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchStatusResponse;
import com.hephaestus.models.BatchStatusResponse.Error;
import com.hephaestus.models.BatchStatusResponse.Output;
import com.microsoft.azure.functions.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/*
 * This function runs every 3 minutes to check find all initiated batches in the table
 * then places a message on a queue to have the batch status url queried for the new status by 
 * the import-processing function.
 */
public class TimerStatusCheck {
    @FunctionName("TimerStatusCheck")
    public void run(
            @TimerTrigger(name = "timerInfo", schedule = "0 * * * * *") String timerInfo,
            final ExecutionContext context) {
        Logger logger = context.getLogger();

        // Initialize TableServiceClient
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("FHIR_STORAGE_CONN_STR"))
                .buildClient();

        // Get a reference to the table
        TableClient tableClient = tableServiceClient.getTableClient(System.getenv("FHIR_STORAGE_TABLE"));

        // Process only entities that have a Batch Status of initiated
        ListEntitiesOptions options = new ListEntitiesOptions().setFilter("BatchStatus eq 'initiated'");
        List<TableEntity> entities = tableClient.listEntities(options, null, null).stream().toList();

        for (TableEntity entity : entities) {
            try {
                Object batchStatusUrlObj = entity.getProperty("BatchStatusUrl");
                if (batchStatusUrlObj == null) {
                    logger.severe("BatchReferenceUrl is null for entity: " + entity.getRowKey());
                    // todo: see note in next exception
                    continue;
                }
                String batchStatusUrl = batchStatusUrlObj.toString();

                URL url;
                try {
                    url = new URL(batchStatusUrl);
                } catch (MalformedURLException e) {
                    logger.severe(batchStatusUrl + " is not a valid URL.");
                    // todo: consider marking is invalid so as not to keep checking in the future...
                    continue;
                }

                // get the access token for fhir
                TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
                TokenRequestContext tokenRequestContext = new TokenRequestContext();
                tokenRequestContext.addScopes(System.getenv("FHIR_SERVER_URL") + "/.default");
                AccessToken authToken = tokenCredential.getTokenSync(tokenRequestContext);

                // fetch the status from the url
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(url.toURI())
                        .header("Authorization", "Bearer " + authToken.getToken())
                        .GET()
                        .build();

                HttpResponse<String> response;
                try {
                    response = client.send(request, BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    logger.severe("Failed to fetch status from " + batchStatusUrl + " due to: " + e.getMessage());
                    // todo: see earlier note about marking invalid but given that this could be a
                    // transient error, maybe not pertinent
                    continue;
                }

                // todo: read the response....
                ObjectMapper mapper = new ObjectMapper();
                BatchStatusResponse batchStatusResponse;

                try {
                    batchStatusResponse = mapper.readValue(response.body(),
                            BatchStatusResponse.class);
                } catch (JsonProcessingException e) {
                    logger.severe(
                            "Failed to deserialize response from " + batchStatusUrl + " due to: " + e.getMessage());
                    return;
                }

                logger.info(batchStatusResponse.toString());

                List<Pair<Output, Error>> results = batchStatusResponse.getAllResults();

                Integer totalSuccessCount = 0;
                Integer totalErrorCount = 0;

                for (Pair<Output, Error> result : results) {
                    String inputUrl = result.getLeft().getInputUrl() != null ? result.getLeft().getInputUrl()
                            : result.getRight().getInputUrl();

                    String[] parts = inputUrl.split("/");
                    String lastPart = parts[parts.length - 1];

                    // todo: update the ndjsonreference in the table that matches our entityId as
                    // partition key and rowkey is the last part
                    TableEntity ndJsonReferenceTableEntity = null;
                    try {
                        ndJsonReferenceTableEntity = tableClient.getEntity(entity.getRowKey(), lastPart);
                    } catch (Exception e) {
                        logger.severe("Failed to process ndJsonReference with filename " + lastPart
                                + " in batch with ID " + entity.getRowKey()
                                + " due to: " + e.getMessage());
                        return;
                    }

                    Integer outputCount = result.getLeft() == null ? 0 : result.getLeft().getCount();
                    totalSuccessCount += outputCount;
                    ndJsonReferenceTableEntity.getProperties().put("OutputCount", outputCount);

                    Integer errorCount = result.getRight() == null ? 0 : result.getRight().getCount();
                    totalErrorCount += errorCount;
                    ndJsonReferenceTableEntity.getProperties().put("ErrorCount", errorCount);

                    tableClient.updateEntity(ndJsonReferenceTableEntity);
                }

                // update the status in the table
                entity.getProperties().put("TotalSuccessCount", totalSuccessCount);
                entity.getProperties().put("TotalErrorCount", totalErrorCount);

                if (totalErrorCount == 0) {
                    entity.getProperties().put("BatchStatus", "succeeded");
                } else if (totalSuccessCount == 0) {
                    entity.getProperties().put("BatchStatus", "fullfailure");
                } else {
                    entity.getProperties().put("BatchStatus", "partialfailure");
                }

                tableClient.updateEntity(entity);
            } catch (Exception e) {
                logger.severe("Failed to process entity: " + entity.getRowKey() + " due to: " + e.getMessage());
            }
        }
    }
}
