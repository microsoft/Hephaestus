package com.hephaestus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.NdJsonReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

public class DurableFunction {
    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "QueueProcessor", queueName = "fhir-hose", connection = "StorageConnStr") String message,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {
        context.getLogger().info(message);

        NdJsonReference ndJsonReference = null;

        // serialize our file reference
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            ndJsonReference = objectMapper.readValue(message,
                    NdJsonReference.class);
            context.getLogger().info("Successfully deserialized message into NdJsonReference.");
        } catch (JsonProcessingException e) {
            context.getLogger().severe("Failed to deserialize message into NdJsonReference: " + e.getMessage());
            throw new RuntimeException("Deserialization error: " + e.getMessage(), e);
        }

        if (ndJsonReference == null) {
            context.getLogger().severe("Failed to deserialize message into NdJsonReference.");
            throw new RuntimeException("Deserialization error.");
        }

        // so we should kick off an orchestration process...
        DurableTaskClient client = durableContext.getClient();

        String instanceId = client.scheduleNewOrchestrationInstance("Orchestration", ndJsonReference);

        // log the instance id
        context.getLogger().info("Initiated orchestration with instance ID: ." + instanceId);
    }

    // how do I do async/await??
    // what is the cool way to do null coalescing in java?
    @FunctionName("Orchestration")
    public void batchOrchestrator(
            @DurableOrchestrationTrigger(name = "Orchestration") TaskOrchestrationContext ctx) {

        NdJsonReference latestFile = ctx.getInput(NdJsonReference.class);

        BatchReference currentBatch = ctx.callActivity("LoadBatchReference", "discardme", BatchReference.class).await();

        final int maxBatchSize = 10; // testing in manageable sizes for now..
        // final int maxBatchSize = 100000000; // real value, 100M

        // if we can't fit the file in the batch we will kick off the current batch
        if (currentBatch.TotalResourceCount + latestFile.lineCount > maxBatchSize) {
            ctx.callActivity("ImportBatch", currentBatch).await();

            // and start a new batch
            currentBatch = new BatchReference();
            currentBatch.BatchStatus = "staging";
        }

        // add the file to the batch
        currentBatch.Files.add(latestFile);
        currentBatch.TotalResourceCount += latestFile.lineCount;

        ctx.callActivity("SaveBatchReference", currentBatch).await();

        // last file we will receive then kick off the batch regardless of size
        if (latestFile.isLastFileInRequest) {
            ctx.callActivity("ImportBatch", currentBatch).await();
        }
    }
}
