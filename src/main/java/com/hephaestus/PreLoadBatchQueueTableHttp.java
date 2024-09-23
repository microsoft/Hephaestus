package com.hephaestus;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.data.tables.models.ListEntitiesOptions;
import com.azure.data.tables.models.TableEntityUpdateMode;

/**
 * Azure Functions with HTTP Trigger.
 */
public class PreLoadBatchQueueTableHttp {
    /**
     * This function listens at endpoint "/api/PreLoadBatchQueueTableHttp". Two ways to invoke it using "curl" command in bash:
     * 1. curl -d "HTTP Body" {your host}/api/PreLoadBatchQueueTableHttp
     * 2. curl {your host}/api/PreLoadBatchQueueTableHttp?name=HTTP%20Query
     */
    @FunctionName("PreLoadBatchQueueTableHttp")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("x");
        String update = request.getQueryParameters().get("update");

        // Initialize TableServiceClient
        TableServiceClient tableServiceClient = new TableServiceClientBuilder()
            .connectionString(System.getenv("StorageConnStr"))
            .buildClient();

        // Get a reference to the table
        TableClient tableClient = tableServiceClient.getTableClient("batchtable");

        if (update != null && update.equalsIgnoreCase("true")) {
            // Update all 'completed' BatchStatus to 'initiated'
            context.getLogger().info("Updating all 'completed' BatchStatus to 'initiated'...");
            ListEntitiesOptions options = new ListEntitiesOptions().setFilter("BatchStatus eq 'completed'");
            tableClient.listEntities(options, null, null).forEach(entity -> {
                entity.getProperties().put("BatchStatus", "initiated");
                tableClient.updateEntity(entity, TableEntityUpdateMode.REPLACE);
            });
            return request.createResponseBuilder(HttpStatus.OK).body("Updated all 'completed' BatchStatus to 'initiated'").build();
        } else if (query != null) {
            try {
                int x = Integer.parseInt(query);
                context.getLogger().info("Adding " + x + " rows to the batchtable...");

                // Add x number of rows to the batchtable
                List<TableEntity> entities = IntStream.range(0, x)
                    .mapToObj(i -> {
                        TableEntity entity = new TableEntity("batch", UUID.randomUUID().toString());
                        entity.getProperties().put("BatchStatus", "initiated");
                        return entity;
                    })
                    .collect(Collectors.toList());

                entities.forEach(tableClient::createEntity);

                return request.createResponseBuilder(HttpStatus.OK).body("Added " + x + " rows to the batchtable").build();
            } catch (NumberFormatException e) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Invalid number format for parameter 'x'").build();
            }
        } else {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a valid parameter 'x' or 'update=true'").build();
        }
    }
}