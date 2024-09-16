package com.hephaestus;

import java.util.Optional;

// Include the following imports to use table APIs
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
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
    
                NdJsonReference latestFile = ctx.getInput<NdJsonReference>();

                // BatchReference currentBatch = ctx.getState(BatchReference.class);

                // ctx.

                // we should receive input of the newest file

                // we should check our current batch size

                // if we can fit the file in the batch we will add it

                final boolean isLastFile = true;
                final int currentBatchSize = 9;
                final int maxBatchSize = 10;
                final int nextFileSize = 2;

                if (currentBatchSize + nextFileSize > maxBatchSize) {
                    // kick off the batch
                    ctx.callActivity("Capitalize", "Tokyo");

                    // start a new batch 
                }
                
                // add the file to the batch

                if(isLastFile) {
                    // kick off the batch
                    ctx.callActivity("Capitalize", "Tokyo");
                }



                // if we can't fit the file in the batch we will kick off the batch or its the last file in the batch we will
                
                // and start a new batch

                // if we 

        //         String result = "";
        // result += ctx.callActivity("Capitalize", "Tokyo", String.class).await() + ", ";
        // result += ctx.callActivity("Capitalize", "London", String.class).await() + ", ";
        // result += ctx.callActivity("Capitalize", "Seattle", String.class).await() + ", ";
        // result += ctx.callActivity("Capitalize", "Austin", String.class).await();
        // return result;


        // consider logging payload sent to import endpoint


        return "finished";
    }

    // todo: make a task? if such a concept exists in java
    @FunctionName("SaveToTable")
    public void saveToTable(@DurableActivityTrigger(name = "batchReference") final BatchReference batchReference, final ExecutionContext context) {
        // setup table client
        String connectionString = System.getenv("StorageConnStr");
        // table should accept filename, line count .... maybe we will add batchId, batchStatus etc
        String tableName = System.getenv("TableName");
        TableServiceClient serviceClient = new TableServiceClientBuilder().connectionString(connectionString).buildClient();
        TableClient tableClient = serviceClient.getTableClient(tableName);

        // create table entity
        for (NdJsonReference file:batchReference.Files) {
            TableEntity entity = new TableEntity(file.FileName, String.valueOf(file.LineCount));    
            tableClient.upsertEntity(entity);
        }

        // todo: return status? make a task and await it?
    }

    @FunctionName("LoadFromTable")
    public BatchReference loadFromTable(@DurableActivityTrigger(name = "batchReference") final ExecutionContext context) {
        String connectionString = System.getenv("StorageConnStr");
        String tableName = System.getenv("TableName");
        TableServiceClient serviceClient = new TableServiceClientBuilder().connectionString(connectionString).buildClient();
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

    


    @FunctionName("QueueProcessor")
    public void runQueueProcessor(
            @QueueTrigger(name = "msg", queueName = "fhir-hose", connection = "StorageConnStr") String message,
            @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
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
        DurableTaskClient client = durableContext.getClient();
        
            
        // get all orchestration instances
        OrchestrationStatusQuery noFilter = new OrchestrationStatusQuery();
        OrchestrationStatusQueryResult result = client.queryInstances(noFilter);
        var instances = result.getOrchestrationState();

        if(instances.size() > 1 ) {
            context.getLogger().info("There are more than one orchestration instances.");
            // this should NEVER happen
        } else if(instances.size() == 1) {
            context.getLogger().info("There is one orchestration instance.");
            // client.
            // this should be the one we just started
        } else {
            context.getLogger().info("There are no orchestration instances.");
            String instanceId = client.scheduleNewOrchestrationInstance("Cities");
            context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
        }


        // todo: check for existing orchestration instance and invoke that if it exists instead of starting a new one


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
