package com.hephaestus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.monitor.ingestion.LogsIngestionClient;
import com.azure.monitor.ingestion.LogsIngestionClientBuilder;
import com.azure.monitor.ingestion.models.LogsUploadException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

public class ProcessLogFileFunction {
    @FunctionName("LogFileProcessingBlobTrigger")
    public void run(
        @BlobTrigger(name = "content", dataType = "binary", path = "fhirimportlogs/{name}", connection = "StorageConnStr") byte[] content,
        final ExecutionContext context
    ) {
        final Logger logger = context.getLogger();
        // read each line of the log file
        String logFileContent = new String(content);
        String[] lines = logFileContent.split("\n");

        // Create Log Ingestion Client
        LogsIngestionClient client = new LogsIngestionClientBuilder()
            .endpoint(System.getenv("LogAnalyticsWorkspaceUrl")) 
            .credential(new DefaultAzureCredentialBuilder().build()) 
            .buildClient(); 

        List<Object> dataList = new ArrayList<>();

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

            dataList.add(item);
        }

        try { 
            client.upload(System.getenv("DataCollectionRuleId"), "Custom-FhirImport_CL", dataList);  
            logger.info("Logs uploaded successfully");
        } catch (LogsUploadException exception) { 
            logger.log(Level.SEVERE, "Failed to upload logs: {0}", exception.getMessage());
        }
    }
}
