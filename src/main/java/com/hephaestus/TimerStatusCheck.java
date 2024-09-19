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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.ImportProcessedQueueMessage;
import com.microsoft.azure.functions.*;

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
                    
                    //if status is not complete, continue to next entity


                    //create message to send to import-processing queue
                    String rowKey = entity.getRowKey();
                    ImportProcessedQueueMessage queueMessage = new ImportProcessedQueueMessage(rowKey, "https://your-status-url.com/status/" + rowKey);

                    // Create a JSON Object Mapper
                    ObjectMapper mapper = new ObjectMapper();
                    String message = mapper.writeValueAsString(queueMessage);

                    // Log the serialized message
                    logger.info("Serialized message: " + message);

                    // Encode the JSON message to Base64
                    String base64Message = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));

                    // Log the length and byte representation of the message
                    logger.info("Message length: " + base64Message.length());
                    logger.info("Message bytes: " + new String(base64Message.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

                    // Validate the message format
                    if (base64Message == null || base64Message.isEmpty()) {
                        logger.severe("Serialized message is null or empty");
                        continue;
                    }

                    // Add message to queue named import-processing
                    queueClient.sendMessage(base64Message);
                    logger.info("Added message to import-processing queue: " + base64Message);

                    // Set the Batch Status to imported based on the rowKey
                    entity.getProperties().put("BatchStatus", "imported");
                    // Save the updated entity back to the table
                    tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);
                    logger.info("Updated Batch Status to: imported for entity: " + entity.getRowKey());
                } catch (Exception e) {
                    logger.severe("Failed to process entity: " + entity.getRowKey() + " due to: " + e.getMessage());
                }
            }
        } 
    }
}
