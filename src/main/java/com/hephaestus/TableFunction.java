package com.hephaestus;

import com.hephaestus.models.BatchReference;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

/*
 * This class contains functions that interact with an Azure Table Storage account.
 * We will use it to persist batch information and the file references within the batch.
 * It will only care about saving and loading the information - not mutating it.
 * 
 * todos: investigate async/await, error handling, and loggiSaveBatchReferenceng
 */

public class TableFunction {
        @FunctionName("SaveBatchReference")
        public void saveToTable(
                        @DurableActivityTrigger(name = "SaveBatchReference") final BatchReference batchReference,
                        final ExecutionContext context) {
                var logger = context.getLogger();
                Helper.saveBatchReference(batchReference, logger);
        }

        // parameter discardString is required to satisfy the DurableActivityTrigger
        @FunctionName("LoadBatchReference")
        public BatchReference loadFromTable(
                        @DurableActivityTrigger(name = "LoadBatchReference") String discardString,
                        final ExecutionContext context) {
                var logger = context.getLogger();
                var batchReference = Helper.loadBatchReference(logger);
                return batchReference;
        }
}
