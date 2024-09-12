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
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.identity.AzureCliCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.identity.ChainedTokenCredential;
import com.azure.identity.ChainedTokenCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;

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

    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "msg", queueName = "fhir-hose", connection = "StorageConnStr") String message,
            final ExecutionContext context) {
        context.getLogger().info(message);

        // setup blob client
        ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder().build();
        AzureCliCredential cliCredential = new AzureCliCredentialBuilder().build();

        ChainedTokenCredential credential = new ChainedTokenCredentialBuilder().addLast(managedIdentityCredential)
                .addLast(cliCredential).build();

        String blobEndpoint = System.getenv("BlobEndpoint");
        String containerName = System.getenv("ContainerName");

        BlobClient blobClient = new BlobClientBuilder()
                .endpoint(blobEndpoint)
                .credential(credential)
                .containerName(containerName)
                .blobName(message)
                .buildClient();

        // read from blob
        String content = new String(blobClient.downloadContent().toBytes());
        context.getLogger().info(content);
    }
}