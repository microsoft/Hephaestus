package com.hephaestus;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.azure.functions.annotation.*;

import reactor.core.publisher.Mono;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchStatusDetails;
import com.hephaestus.models.ImportProcessedQueueMessage;
import com.microsoft.azure.functions.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

/**
 * Azure Functions with Timer trigger.
 */
public class TimerStatusCheck {
    /**
     * This function will be invoked periodically according to the specified schedule.
     */
    @FunctionName("TimerStatusCheck")
    public void run(
        @TimerTrigger(name = "timerInfo", schedule = "0 */1 * * * *") String timerInfo,
        final ExecutionContext context
    ) {
        Logger logger = context.getLogger();
        logger.info("Java Timer trigger function executed at: " + LocalDateTime.now());

        // Initialize TableServiceClient
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
            .connectionString(System.getenv("AzureWebJobsStorage"))
            .buildClient();

        // Get a reference to the table
        TableClient tableClient = tableServiceClient.getTableClient("batchtable");

        // Initialize QueueClient
        QueueClient queueClient = new QueueClientBuilder()
            .connectionString(System.getenv("AzureWebJobsStorage"))
            .queueName("import-processing")
            .buildClient();

        // Query the table for the latest records
        ListEntitiesOptions options = new ListEntitiesOptions().setFilter("BatchStatus eq 'initiated'");
        List<TableEntity> entities = tableClient.listEntities(options, null, null).stream().toList();

        // Process only entities that have a Batch Status of initiated
        for (TableEntity entity : entities) {
            logger.info("Processing entity: " + entity.getRowKey());
            //log the batch status of the entity
            logger.info("Batch Status: " + entity.getProperties().get("BatchStatus"));

            try {
                //check status of the batch using the statusUrl using http client
                String statusUrl = entity.getProperties().get("BatchStatusUrl").toString();
                String rowKey = entity.getRowKey();

                // Check the status of the batch, and return the details
                Map<String, BatchStatusDetails> batchStatusDetailsMap = checkStatusAndLogErrors(tableClient, entity.getPartitionKey(), entity.getRowKey(), statusUrl, logger);

                //if status is not complete, continue to next entity
                if (!batchStatusDetailsMap.isEmpty()) {
                    //create message to send to import-processing queue
                    ImportProcessedQueueMessage queueMessage = new ImportProcessedQueueMessage(rowKey, statusUrl);

                    // Create a JSON Object Mapper
                    ObjectMapper mapper = new ObjectMapper();
                    String message = mapper.writeValueAsString(queueMessage);

                    // Serialize the batchStatusDetailsMap values to a JSON array
                    String batchStatusDetailsJson = mapper.writeValueAsString(batchStatusDetailsMap.values());

                    // Log the serialized message
                    logger.info("Serialized message: " + message);
                    logger.info("Serialized BatchStatusDetails: " + batchStatusDetailsJson);

                    // Combine the message and batchStatusDetailsJson into a single JSON object
                    String combinedMessage = String.format("{\"message\": %s, \"batchStatusDetails\": %s}", message, batchStatusDetailsJson);

                    // Encode the combined JSON message to Base64
                    String base64Message = Base64.getEncoder().encodeToString(combinedMessage.getBytes(StandardCharsets.UTF_8));

                    // Add message to queue named import-processing
                    queueClient.sendMessage(base64Message);
                    logger.info("Added message to import-processing queue: " + base64Message);

                    // Set the Batch Status to imported based on the rowKey
                    entity.getProperties().put("BatchStatus", "imported");
                    // Save the updated entity back to the table
                    tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);
                    logger.info("Updated Batch Status to: imported for entity: " + entity.getRowKey());
                } else {
                    logger.info("Status is not ok, continue to next entity");
                }
            } catch (Exception e) {
                logger.severe("Failed to process entity: " + entity.getRowKey() + " due to: " + e.getMessage());
            }
        } 
    }

    private static Map<String, BatchStatusDetails> checkStatusAndLogErrors(TableClient tableClient, String partitionKey, String rowKey, String statusUrl, Logger logger) throws Exception {
        logger.info("Attempting to check status of: " + statusUrl);
        //need to add an authorization header, use default credential
        TokenCredential tokenCredential = new DefaultAzureCredentialBuilder().build();
        Mono<AccessToken> tokenMono = tokenCredential.getToken(new TokenRequestContext().addScopes(System.getenv("TimerStatusCheckAccessScopes")));
        AccessToken token = tokenMono.block();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(statusUrl))
            .header("Authorization", "Bearer " + token.getToken())
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("Status check response: " + response.body());

        Map<String, BatchStatusDetails> batchStatusDetailsMap = new HashMap<>();

        if (response.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.body());

            // Process the output counts
            JsonNode outputArray = rootNode.path("output");
            for (JsonNode outputNode : outputArray) {
                String inputUrl = outputNode.path("inputUrl").asText();
                int successCount = outputNode.path("count").asInt();

                batchStatusDetailsMap.compute(inputUrl, (key, details) -> {
                    if (details == null) {
                        return new BatchStatusDetails(inputUrl, successCount, 0, null);
                    } else {
                        details.setSuccessCount(details.getSuccessCount() + successCount);
                        return details;
                    }
                });
            }

            // Process the error counts
            JsonNode errorArray = rootNode.path("error");
            for (JsonNode errorNode : errorArray) {
                String inputUrl = errorNode.path("inputUrl").asText();
                String errorUrl = errorNode.path("url").asText();
                int errorCount = errorNode.path("count").asInt();

                batchStatusDetailsMap.compute(inputUrl, (key, details) -> {
                    if (details == null) {
                        return new BatchStatusDetails(inputUrl, 0, errorCount, inputUrl);
                    } else {
                        details.setErrorCount(details.getErrorCount() + errorCount);
                        details.setErrorUrl(errorUrl);
                        return details;
                    }
                });
            }
        }

        // Log the counts
        for (BatchStatusDetails details : batchStatusDetailsMap.values()) {
            logger.info(details.toString());
        }

        return batchStatusDetailsMap;
    }

}
