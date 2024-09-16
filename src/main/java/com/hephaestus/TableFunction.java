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

public  class TableFunction{
    // todo: make a task? if such a concept exists in java
    @FunctionName("SaveToTable")
    public void saveToTable(@DurableActivityTrigger(name = "batchReference") final BatchReference batchReference,
            final ExecutionContext context) {
        // setup table client
        String connectionString = System.getenv("StorageConnStr");
        // table should accept filename, line count .... maybe we will add batchId,
        // batchStatus etc
        String tableName = System.getenv("TableName");
        TableServiceClient serviceClient = new TableServiceClientBuilder().connectionString(connectionString)
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(tableName);

        // create table entity
        for (NdJsonReference file : batchReference.Files) {
            TableEntity entity = new TableEntity(file.FileName, String.valueOf(file.LineCount));
            tableClient.upsertEntity(entity);
        }

        // todo: return status? make a task and await it?
    }

    @FunctionName("LoadFromTable")
    public BatchReference loadFromTable(
            @DurableActivityTrigger(name = "batchReference") final ExecutionContext context) {
        String connectionString = System.getenv("StorageConnStr");
        String tableName = System.getenv("TableName");
        TableServiceClient serviceClient = new TableServiceClientBuilder().connectionString(connectionString)
                .buildClient();
        TableClient tableClient = serviceClient.getTableClient(tableName);

        BatchReference batchReference = new BatchReference();

        for (TableEntity entity : tableClient.listEntities()) {
            NdJsonReference file = new NdJsonReference();
            file.FileName = entity.getPartitionKey();
            file.LineCount = Integer.parseInt(entity.getRowKey());
            batchReference.Files.add(file);
            batchReference.TotalResourceCount += file.LineCount;
        }

        return batchReference;
    }
}
