package com.hephaestus;

import com.microsoft.azure.functions.annotation.*;

import java.util.List;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.ImportProcessedQueueMessage;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with Azure Storage Queue trigger.
 */
public class ImportProcessingQueueTrigger {
    /**
     * This function will be invoked when a new message is received at the specified path. The message contents are provided as input to this function.
     */
    @FunctionName("ImportProcessingQueueTrigger")
    public void run(
        @QueueTrigger(name = "message", queueName = "import-processing", connection = "AzureWebJobsStorage") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: " + message);

         try {
            context.getLogger().info("Deserializing the message...");
            // Deserialize the message
            ImportProcessedQueueMessage importProcessedQueueMessage = new ObjectMapper().readValue(message, ImportProcessedQueueMessage.class);

            context.getLogger().info("Initializing TableServiceClient...");
            // Initialize TableServiceClient
            TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("AzureWebJobsStorage"))
                .buildClient();

            context.getLogger().info("Getting a reference to the table...");
            // Get a reference to the table
            TableClient tableClient = tableServiceClient.getTableClient("batchtable");

            context.getLogger().info("Querying the entity from the table...");
            // Query entity by rowKey
            ListEntitiesOptions options = new ListEntitiesOptions().setFilter("RowKey eq '" + importProcessedQueueMessage.getRowKey() + "'");
            List<TableEntity> entities = tableClient.listEntities(options, null, null).stream().toList();

            if (entities.isEmpty()) {
                context.getLogger().severe("Entity with RowKey: " + importProcessedQueueMessage.getRowKey() + " not found.");
                return;
            }

            TableEntity entity = entities.get(0);

            context.getLogger().info("Updating the BatchStatus column to completed...");
            // Update the BatchStatus column to completed
            entity.getProperties().put("BatchStatus", "completed");

            context.getLogger().info("Saving the updated entity back to the table...");
            // Save the updated entity back to the table
            tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);

            context.getLogger().info("Updated BatchStatus to completed for entity with RowKey: " + importProcessedQueueMessage.getRowKey());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            context.getLogger().severe("JSON deserialization error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            context.getLogger().severe("Invalid argument: " + e.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("General error: " + e.getMessage());
            e.printStackTrace();
        }

    }
}
