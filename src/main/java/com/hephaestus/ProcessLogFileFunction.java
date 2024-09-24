package com.hephaestus;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.ingestion.models.LogsIngestionClient;

public class ProcessLogFileFunction {
    @FunctionName("LogFileProcessingBlobTrigger")
    public void run(
        @BlobTrigger(name = "content", dataType = "binary", path = "{name}", connection = "StorageConnStr") byte[] content,
        final ExecutionContext context
    ) {
        // read each line of the log file
        String logFileContent = new String(content);
        String[] lines = logFileContent.split("\n");

        // Create Log Ingestion Client
        LogsIngestionClient client = new LogsIngestionClientBuilder()
            .endpoint("https://my-url.monitor.azure.com") 
            .credential(new DefaultAzureCredentialBuilder().build()) 
            .buildClient(); 

        List<Object> dataList = new ArrayList<Object>();
        

        try { 
            client.upload("dcr-00000000000000000000000000000000", "Custom-MyTableRawData", dataList);  
            System.out.println("Logs uploaded successfully"); 
        } catch (LogsUploadException exception) { 
            System.out.println("Failed to upload logs "); 
            exception.getLogsUploadErrors() 
                .forEach(httpError -> System.out.println(httpError.getMessage())); 
        } 

        // write each line to log analytics table
        for (String line : lines) {
            context.getLogger().info(line);
            Object item = new Object() { 
                OffsetDateTime time = OffsetDateTime.now(); 
                String computer = "Computer1"; 
                Object additionalContext = new Object() { 
                    String instanceName = "user4"; 
                    String timeZone = "Pacific Time"; 
                    int level = 4; 
                    String counterName = "AppMetric1"; 
                    double counterValue = 15.3; 
                }; 
            };

        }
    }
}
