package com.hephaestus;

import com.microsoft.azure.functions.annotation.*;

import java.util.List;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.TableEntityUpdateMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchStatusDetails;
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
        @QueueTrigger(name = "message", queueName = "import-processing", connection = "StorageConnStr") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: " + message);

         try {
            context.getLogger().info("Deserializing the message...");
            // Deserialize the combined message
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(message);

            // Extract the original message
            JsonNode messageNode = rootNode.path("message");
            ImportProcessedQueueMessage importProcessedQueueMessage = mapper.treeToValue(messageNode, ImportProcessedQueueMessage.class);

            // Extract the BatchStatusDetails array
            JsonNode batchStatusDetailsNode = rootNode.path("batchStatusDetails");
            List<BatchStatusDetails> batchStatusDetailsList = mapper.readValue(batchStatusDetailsNode.toString(), mapper.getTypeFactory().constructCollectionType(List.class, BatchStatusDetails.class));

            context.getLogger().info("Initializing TableServiceClient...");
            // Initialize TableServiceClient
            TableServiceClient tableServiceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr"))
                .buildClient();

            context.getLogger().info("Getting a reference to the table...");
            // Get a reference to the table
            TableClient tableClient = tableServiceClient.getTableClient("batchtable");

            // Process batchstatusdetails for the individual urls
            for (BatchStatusDetails batchStatusDetails : batchStatusDetailsList) {
                // get the root file name by parsing the inputUrl to get the last segment, that is our RowKey
                String[] segments = batchStatusDetails.getInputUrl().split("/");
                String rowKey = segments[segments.length - 1];

                // get the parition key from the importProcessedQueueMessage, which is the RowKey of the entity
                String filePartitionKey = importProcessedQueueMessage.getRowKey();

                // get the entity with the BatchStatusDetails
                TableEntity entity = getEntityByRowKeyAndPartitionKey(tableClient, filePartitionKey, rowKey, context);

                // add a successCount and errorCount to the entity
                entity.getProperties().put("SuccessCount", batchStatusDetails.getSuccessCount());
                entity.getProperties().put("ErrorCount", batchStatusDetails.getErrorCount());

                // if there are errors, add the errorUrl to the entity
                if (batchStatusDetails.getErrorCount() > 0) {
                    entity.getProperties().put("ErrorUrl", batchStatusDetails.getErrorUrl());
                }

                // update the entity
                tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);
            }

            context.getLogger().info("Update the BatchStatus to completed for entity with RowKey: " + importProcessedQueueMessage.getRowKey());
            // Query entity by rowKey and partitionKey
            TableEntity entity = getEntityByRowKeyAndPartitionKey(tableClient, "batch", importProcessedQueueMessage.getRowKey(), context);

            if (entity == null) {
                context.getLogger().severe("Entity with RowKey: " + importProcessedQueueMessage.getRowKey() + " not found.");
                return;
            }

            context.getLogger().info("Serializing BatchStatusDetails to JSON...");
            // Serialize the BatchStatusDetails list to JSON
            String batchStatusDetailsJson = mapper.writeValueAsString(batchStatusDetailsList);

            context.getLogger().info("Updating the BatchStatus column to completed...");
            // Update the BatchStatus column to completed
            entity.getProperties().put("BatchStatusDetails", batchStatusDetailsJson);

            // update status to either succeeded, partiallyFailed or fullyFailed
            if (batchStatusDetailsList.stream().anyMatch(b -> b.getErrorCount() > 0)) {
                entity.getProperties().put("BatchStatus", "partiallyFailed");
            } else if (batchStatusDetailsList.stream().allMatch(b -> b.getErrorCount() == 0)) {
                entity.getProperties().put("BatchStatus", "succeeded");
            } else {
                entity.getProperties().put("BatchStatus", "fullyFailed");
            }

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

    private TableEntity getEntityByRowKeyAndPartitionKey(TableClient tableClient, String partitionKey, String rowKey, ExecutionContext context) {
        ListEntitiesOptions options = new ListEntitiesOptions().setFilter("PartitionKey eq '" + partitionKey + "' and RowKey eq '" + rowKey + "'");
        List<TableEntity> entities = tableClient.listEntities(options, null, null).stream().toList();

        if (entities.isEmpty()) {
            return null;
        }

        return entities.get(0);
    }
}
