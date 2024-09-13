package com.hephaestus;

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
import com.microsoft.durabletask.TaskOrchestrationContext;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.hephaestus.models.NdJsonReference;
import com.azure.identity.AzureCliCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredential;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Azure Functions with HTTP Trigger.
 */
public class Function {
    /**
     * This function listens at endpoint "/api/HttpExample". Two ways to invoke it
     * using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/HttpExample
     * 2. curl "{your host}/api/HttpExample?name=HTTP%20Query"
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage runHttpExample(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET,
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        String blobEndpoint = System.getenv("BlobEndpoint");
        String containerName = System.getenv("ContainerName");

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }

    /**
     * This HTTP-triggered function starts the orchestration.
     */
    @FunctionName("StartOrchestration")
    public HttpResponseMessage startOrchestration(
            @HttpTrigger(name = "req", methods = { HttpMethod.GET,
                    HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        DurableTaskClient client = durableContext.getClient();
        String instanceId = client.scheduleNewOrchestrationInstance("Cities");
        context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
        return durableContext.createCheckStatusResponse(request, instanceId);
    }

    /**
     * This is the orchestrator function, which can schedule activity functions,
     * create durable timers,
     * or wait for external events in a way that's completely fault-tolerant.
     */
    @FunctionName("Cities")
    public String citiesOrchestrator(
            @DurableOrchestrationTrigger(name = "taskOrchestrationContext") TaskOrchestrationContext ctx) {
        String result = "";
        result += ctx.callActivity("Capitalize", "Tokyo", String.class).await() + ", ";
        result += ctx.callActivity("Capitalize", "London", String.class).await() + ", ";
        result += ctx.callActivity("Capitalize", "Seattle", String.class).await() + ", ";
        result += ctx.callActivity("Capitalize", "Austin", String.class).await();
        return result;
    }

    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName("Capitalize")
    public String capitalize(@DurableActivityTrigger(name = "name") String name, final ExecutionContext context) {
        context.getLogger().info("Capitalizing: " + name);
        return name.toUpperCase();
    }

    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "msg", queueName = "fhir-hose", connection = "StorageConnStr") String message,
            final ExecutionContext context) {
        context.getLogger().info(message);

        // serialize our file reference
        // try {
        // ObjectMapper objectMapper = new ObjectMapper();
        // NdJsonReference ndJsonReference = objectMapper.readValue(message,
        // NdJsonReference.class);
        // context.getLogger().info("Successfully deserialized message into
        // NdJsonReference.");
        // } catch (JsonProcessingException e) {
        // context.getLogger().severe("Failed to deserialize message into
        // NdJsonReference: " + e.getMessage());
        // throw new RuntimeException("Deserialization error: " + e.getMessage(), e);
        // }

        // so we should kick off an orchestration process...

        // // setup blob client
        // ManagedIdentityCredential managedIdentityCredential = new
        // ManagedIdentityCredentialBuilder().build();
        // AzureCliCredential cliCredential = new AzureCliCredentialBuilder().build();

        // ChainedTokenCredential credential = new
        // ChainedTokenCredentialBuilder().addLast(managedIdentityCredential)
        // .addLast(cliCredential).build();

        // String blobEndpoint = System.getenv("BlobEndpoint");
        // String containerName = System.getenv("ContainerName");

        // BlobClient blobClient = new BlobClientBuilder()
        // .endpoint(blobEndpoint)
        // .credential(credential)
        // .containerName(containerName)
        // .blobName(message)
        // .buildClient();

        // // read from blob
        // String content = new String(blobClient.downloadContent().toBytes());
        // context.getLogger().info(content);
    }
}
