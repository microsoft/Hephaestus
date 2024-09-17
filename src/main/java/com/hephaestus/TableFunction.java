package com.hephaestus;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.NdJsonReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import java.util.UUID;

/*
 * This class contains functions that interact with an Azure Table Storage account.
 * We will use it to persist batch information and the file references within the batch.
 * It will only care about saving and loading the information - not mutating it.
 * 
 * todos: investigate async/await, error handling, and logging
 */

public class TableFunction {
    @FunctionName("SaveBatchReference")
    public void saveToTable(@DurableActivityTrigger(name = "batchReference") final BatchReference batchReference,
            final ExecutionContext context) {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr"))
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(System.getenv("TableName"));
        context.getLogger().info("Saving batch reference " + batchReference.BatchId + " to table storage.");

        // save batch reference
        TableEntity batchEntity = new TableEntity("batch", batchReference.BatchId.toString());
        batchEntity.addProperty("TotalResourceCount", batchReference.TotalResourceCount);
        batchEntity.addProperty("BatchStatus", batchReference.BatchStatus);
        tableClient.upsertEntity(batchEntity);

        context.getLogger().info("Saving " + batchReference.Files.size() + " file references for batch "
                + batchReference.BatchId + " to table storage.");
        // save file references
        for (NdJsonReference file : batchReference.Files) {
            TableEntity entity = new TableEntity(batchReference.BatchId.toString(), file.filename);
            entity.addProperty("LineCount", file.lineCount);
            tableClient.upsertEntity(entity);
        }

        // todo: return status? make a task and await it?
    }

    @FunctionName("LoadBatchReference")
    public BatchReference loadFromTable(
            @DurableActivityTrigger(name = "batchReference") final ExecutionContext context) {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr"))
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(System.getenv("TableName"));

        context.getLogger().info("Loading staging batch reference from table storage.");

        // fetch only the rows whose partition key is "batch" as the batch reference
        // check for status staging
        // there should only ever be one batch reference in staging status
        BatchReference currentBatch = tableClient
                .listEntities()
                .stream()
                .filter(entity -> entity.getPartitionKey().equals("batch")
                        && entity.getProperty("BatchStatus").equals("staging"))
                .map(entity -> {
                    BatchReference batchReference = new BatchReference();
                    batchReference.BatchId = UUID.fromString(entity.getRowKey());
                    batchReference.TotalResourceCount = Integer
                            .parseInt(entity.getProperty("TotalResourceCount").toString());
                    batchReference.BatchStatus = entity.getProperty("BatchStatus").toString();
                    return batchReference;
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No batch reference found with status 'staging'"));

        if (currentBatch == null) {
            // for initial run
            return new BatchReference() {
                {
                    BatchStatus = "staging";
                }
            };
        }

        // fetch file references associated with the batch
        currentBatch.Files = tableClient
                .listEntities()
                .stream()
                .filter(entity -> entity.getPartitionKey().equals(currentBatch.BatchId.toString()))
                .map(entity -> {
                    NdJsonReference ndJsonReference = new NdJsonReference();
                    ndJsonReference.filename = entity.getRowKey();
                    ndJsonReference.lineCount = Integer.parseInt(entity.getProperty("LineCount").toString());
                    return ndJsonReference;
                })
                .toList();

        context.getLogger().info("Loaded staging batch reference " + currentBatch.BatchId + " from table storage.");

        return currentBatch;
    }
}
