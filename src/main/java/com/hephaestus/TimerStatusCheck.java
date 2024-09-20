package com.hephaestus;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import com.microsoft.azure.functions.annotation.*;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        List<TableEntity> entities = tableClient.listEntities().stream().toList();

        // Process only entities that have a Batch Status of initiated
        for (TableEntity entity : entities) {
            if (entity.getProperties().get("BatchStatus").toString().equals("initiated")) {
                logger.info("Processing entity: " + entity.getRowKey());
                //log the batch status of the entity
                logger.info("Batch Status: " + entity.getProperties().get("BatchStatus"));

                try {
                    //check status of the batch using the statusUrl using http client
                    String statusUrl = entity.getProperties().get("StatusUrl").toString();
                    String rowKey = entity.getRowKey();
                    boolean isStatusOk = checkStatusAndLogErrors(tableClient, entity.getPartitionKey(), entity.getRowKey(), statusUrl,logger);

                    //if status is not complete, continue to next entity
                    if (isStatusOk) {
                        //create message to send to import-processing queue
                        ImportProcessedQueueMessage queueMessage = new ImportProcessedQueueMessage(rowKey, statusUrl);

                        // Create a JSON Object Mapper
                        ObjectMapper mapper = new ObjectMapper();
                        String message = mapper.writeValueAsString(queueMessage);

                        // Log the serialized message
                        logger.info("Serialized message: " + message);

                        // Encode the JSON message to Base64
                        String base64Message = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));

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
    }

    private static boolean checkStatusAndLogErrors(TableClient tableClient, String partitionKey, String rowKey, String statusUrl, Logger logger) throws Exception {
        logger.info("Attempting to check status of: " + statusUrl);
        //need to add an authorization header, use default credential
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(new URI(statusUrl))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("Status check response: " + response.body());

        if (response.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(response.body());
            JsonNode errorArray = rootNode.path("error");

            if (errorArray.isArray()) {
                for (JsonNode errorNode : errorArray) {
                    String errorUrl = errorNode.path("url").asText();
                    logger.info("Error URL: " + errorUrl);
                }
            }
            return true;
        } else {
            //update the timestamp on the entity
            TableEntity entity = tableClient.getEntity(partitionKey, rowKey);
            entity.getProperties().put("Timestamp", OffsetDateTime.now().toString());
            tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);
            
            logger.info("Status check returned non-200 response, keep it in the table: " + response.statusCode());
            return false;
        }
    }
}
