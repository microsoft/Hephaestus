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

import java.util.UUID;
import java.util.logging.Logger;
import java.util.ArrayList;

public class DurableFunction {
    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "QueueProcessor", queueName = "fhir-hose", connection = "FHIR_STORAGE_CONN_STR") String message,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {

        final Logger logger = context.getLogger();
        logger.info(message);

        NdJsonReference ndJsonReference = null;

        // serialize our file reference
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            ndJsonReference = objectMapper.readValue(message,
                    NdJsonReference.class);
            logger.info("Successfully deserialized message into NdJsonReference.");
        } catch (JsonProcessingException e) {
            logger.severe("Failed to deserialize message into NdJsonReference: " + e.getMessage());
            throw new RuntimeException("Deserialization error: " + e.getMessage(), e);
        }

        if (ndJsonReference == null) {
            logger.severe("Failed to deserialize message into NdJsonReference.");
            throw new RuntimeException("Deserialization error.");
        }

        final String maxBatchSizeStr = System.getenv("MAX_BATCH_SIZE");
        final String suggestedMinFileSizeStr = System.getenv("SUGGESTED_MIN_FILE_SIZE");

        final int maxBatchSize = maxBatchSizeStr != null ? Integer.parseInt(maxBatchSizeStr) : 100_000_000;
        final int suggestedMinFileSize = suggestedMinFileSizeStr != null ? Integer.parseInt(maxBatchSizeStr) : 20_000;

        // for now, just warn us when a file is not matching recommendations but don't
        // stop the process
        if (ndJsonReference.lineCount < suggestedMinFileSize) {
            logger.warning("For the file " +
                    ndJsonReference.filename +
                    " the line count of " +
                    ndJsonReference.lineCount +
                    "is less than " +
                    suggestedMinFileSizeStr +
                    " is the suggested minimum file size for this process.");
        }

        // exit early with logging if the file is too large to be included in any batch
        if (ndJsonReference.lineCount > maxBatchSize) {
            logger.severe("For the file " +
                    ndJsonReference.filename +
                    " the line count of " +
                    ndJsonReference.lineCount +
                    "is greater than " +
                    maxBatchSizeStr +
                    " is the maximum batch size for this process.");

            return;
        }

        // so we should kick off an orchestration process...
        DurableTaskClient client = durableContext.getClient();

        String instanceId = client.scheduleNewOrchestrationInstance("Orchestration", ndJsonReference);

        // log the instance id
        logger.info("Initiated orchestration with instance ID: ." + instanceId);
    }

    // how do I do async/await??
    // what is the cool way to do null coalescing in java?
    @FunctionName("Orchestration")
    public void batchOrchestrator(
            @DurableOrchestrationTrigger(name = "Orchestration") TaskOrchestrationContext ctx) {

        NdJsonReference latestFile = ctx.getInput(NdJsonReference.class);

        BatchReference currentBatch = ctx.callActivity("LoadBatchReference", "discardme", BatchReference.class).await();

        final String maxBatchSizeStr = System.getenv("MAX_BATCH_SIZE");

        final int maxBatchSize = maxBatchSizeStr != null ? Integer.parseInt(maxBatchSizeStr) : 100_000_000;

        // if we can't fit the file in the batch we will kick off the current batch
        if (currentBatch.TotalResourceCount + latestFile.lineCount > maxBatchSize) {
            ctx.callActivity("ImportBatch", currentBatch).await();

            // and start a new batch
            currentBatch = new BatchReference();
            currentBatch.BatchId = UUID.randomUUID();
            currentBatch.BatchStatus = "staging";
            currentBatch.Files = new ArrayList<NdJsonReference>();
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
