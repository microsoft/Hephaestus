package com.hephaestus;

import com.azure.core.util.logging.LogLevel;
// Include the following imports to use table APIs
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

import io.netty.util.internal.logging.Log4J2LoggerFactory;

import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class DurableFunction {
    private final Logger _logger = Logger.getLogger(DurableFunction.class.getName());

    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "msg", queueName = "fhir-hose", connection = "StorageConnStr") String message,
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

        // // get all orchestration instances
        // OrchestrationStatusQuery noFilter = new OrchestrationStatusQuery();
        // OrchestrationStatusQueryResult result = client.queryInstances(noFilter);
        // var instances = result.getOrchestrationState();

        // if (instances.size() > 1) {
        // context.getLogger().info("There are more than one orchestration instances.");
        // // this should NEVER happen
        // } else if (instances.size() == 1) {
        // context.getLogger().info("There is one orchestration instance.");
        // // client.
        // // this should be the one we just started
        // } else {
        // context.getLogger().info("There are no orchestration instances.");
        // String instanceId = client.scheduleNewOrchestrationInstance("Orchestration",
        // ndJsonReference);
        // context.getLogger().info("Created new Java orchestration with instance ID = "
        // + instanceId);
        // }
    }

    // how do I do async/await??
    // what is the cool way to do null coalescing in java?
    @FunctionName("Orchestration")
    public String batchOrchestrator(
            @DurableOrchestrationTrigger(name = "taskOrchestrationContext") TaskOrchestrationContext ctx) {

        NdJsonReference latestFile = ctx.getInput(NdJsonReference.class);
        BatchReference currentBatch = ctx.callActivity("LoadFromTable", BatchReference.class).await();

        final int maxBatchSize = 10; // testing in manageable sizes for now..
        // final int maxBatchSize = 100000000; // real value, 100M

        // if we can't fit the file in the batch we will kick off the current batch
        if (currentBatch.TotalResourceCount + latestFile.lineCount > maxBatchSize) {
            ctx.callActivity("Import", currentBatch).await();

            // we should consider moving the change to batch status and saving of that to
            // the initiate export activity
            currentBatch.BatchStatus = "initiated";
            ctx.callActivity("SaveBatchReference", currentBatch).await();

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
            ctx.callActivity("InitiateExport", currentBatch).await();
            currentBatch.BatchStatus = "initiated";
            ctx.callActivity("SaveBatchReference", currentBatch).await();
        }

        // consider logging payload sent to import endpoint; probably can be handled by
        // export activity
        return "finished";
    }
}
