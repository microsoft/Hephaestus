package com.hephaestus;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.NdJsonReference;
import java.util.logging.Logger;
import java.util.List;
import java.util.UUID;

/*
 * Java Azure Functions don't seem to allow for initiating an activity function 
 * from within another activity function. So we've extracted logic for reuse here
 */
public class Helper {
    public static BatchReference loadBatchReference(Logger logger) {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr"))
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(System.getenv("TableName"));

        logger.info("Loading staging batch reference from table storage.");

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
                .orElse(null);

        // for initial run
        if (currentBatch == null) {
            logger.info("No staging batch reference found in table storage. Creating new batch reference.");
            currentBatch = new BatchReference();
            currentBatch.BatchId = UUID.randomUUID();
            currentBatch.TotalResourceCount = 0;
            currentBatch.BatchStatus = "staging";
            currentBatch.Files = List.of();
            return currentBatch;
        }

        // fetch file references associated with the batch
        final BatchReference finalCurrentBatch = currentBatch;
        currentBatch.Files = tableClient
                .listEntities()
                .stream()
                .filter(entity -> entity.getPartitionKey().equals(finalCurrentBatch.BatchId.toString()))
                .map(entity -> {
                    NdJsonReference ndJsonReference = new NdJsonReference();
                    ndJsonReference.filename = entity.getRowKey();
                    ndJsonReference.lineCount = Integer.parseInt(entity.getProperty("LineCount").toString());
                    return ndJsonReference;
                })
                .toList();

        logger.info("Loaded staging batch reference " + currentBatch.BatchId + " from table storage.");

        return currentBatch;
    }

    public static void saveBatchReference(BatchReference batchReference, Logger logger) {
        TableServiceClient serviceClient = new TableServiceClientBuilder()
                .connectionString(System.getenv("StorageConnStr"))
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(System.getenv("TableName"));
        logger.info("Saving batch reference " + batchReference.BatchId + " to table storage.");

        // save batch reference
        TableEntity batchEntity = new TableEntity("batch", batchReference.BatchId.toString());
        batchEntity.addProperty("TotalResourceCount", batchReference.TotalResourceCount);
        batchEntity.addProperty("BatchStatus", batchReference.BatchStatus);
        tableClient.upsertEntity(batchEntity);

        logger.info("Saving " + batchReference.Files.size() + " file references for batch "
                + batchReference.BatchId + " to table storage.");
        // save file references
        for (NdJsonReference file : batchReference.Files) {
            TableEntity entity = new TableEntity(batchReference.BatchId.toString(), file.filename);
            entity.addProperty("LineCount", file.lineCount);
            tableClient.upsertEntity(entity);
        }
        // todo: return status? make a task and await it?
    }
}
