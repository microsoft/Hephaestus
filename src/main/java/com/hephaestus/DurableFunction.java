package com.hephaestus;

import java.util.Optional;

// Include the following imports to use table APIs
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hephaestus.models.BatchReference;
import com.hephaestus.models.NdJsonReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.OrchestrationStatusQuery;
import com.microsoft.durabletask.OrchestrationStatusQueryResult;
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

public class DurableFunction {
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
            ndJsonReference = objectMapper.readValue(message,
            NdJsonReference.class);
            context.getLogger().info("Successfully deserialized message into NdJsonReference.");
        } catch (JsonProcessingException e) {
            context.getLogger().severe("Failed to deserialize message into NdJsonReference: " + e.getMessage());
            throw new RuntimeException("Deserialization error: " + e.getMessage(), e);
        }

        if(ndJsonReference == null) {
            context.getLogger().severe("Failed to deserialize message into NdJsonReference.");
            throw new RuntimeException("Deserialization error.");
        }

        // so we should kick off an orchestration process...
        DurableTaskClient client = durableContext.getClient();

        // get all orchestration instances
        OrchestrationStatusQuery noFilter = new OrchestrationStatusQuery();
        OrchestrationStatusQueryResult result = client.queryInstances(noFilter);
        var instances = result.getOrchestrationState();

        if (instances.size() > 1) {
            context.getLogger().info("There are more than one orchestration instances.");
            // this should NEVER happen
        } else if (instances.size() == 1) {
            context.getLogger().info("There is one orchestration instance.");
            // client.
            // this should be the one we just started
        } else {
            context.getLogger().info("There are no orchestration instances.");
            String instanceId = client.scheduleNewOrchestrationInstance("Orchestration", ndJsonReference);
            context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
        }
    }

    // how do I do async/await??
    // what is the cool way to do null coalescing in java?
    @FunctionName("Orchestration")
    public String citiesOrchestrator(
            @DurableOrchestrationTrigger(name = "taskOrchestrationContext") TaskOrchestrationContext ctx) {

        NdJsonReference latestFile = ctx.getInput(NdJsonReference.class);
        BatchReference currentBatch = ctx.callActivity("LoadFromTable", BatchReference.class).await();
        if (currentBatch == null) {
            currentBatch = new BatchReference();
        }

        // we should check our current batch size

        // if we can fit the file in the batch we will add it

        // final boolean isLastFile = true;
        // final int currentBatchSize = 9;
        final int maxBatchSize = 10; // testing in manageable sizes for now..
        // final int maxBatchSize = 100000000; // real value, 100M
        // final int nextFileSize = 2;

        if (currentBatch.TotalResourceCount + latestFile.LineCount > maxBatchSize) {
            // kick off the batch
            ctx.callActivity("Capitalize", "Tokyo");

            // start a new batch
        }

        // add the file to the batch

        if (latestFile.IsLastFileInRequest) {
            // kick off the batch
            ctx.callActivity("Capitalize", "Tokyo");
        }

        // if we can't fit the file in the batch we will kick off the batch or its the
        // last file in the batch we will

        // and start a new batch

        // if we

        // consider logging payload sent to import endpoint

        return "finished";
    }

}
