# Project

This MVP sample project is an orchestration process for ingesting large volumes of FHIR bundles (ndjson) into an Azure Health Data Services FHIR Api using the $import endpoint. Following the documented [Performance Considerations](https://learn.microsoft.com/en-us/azure/healthcare-apis/fhir/import-data#performance-considerations), this project aims to achieve maximum scaling efficiency by batching files according to these specifications:
- ndjson files should contain at least 20k resources
- import batch should include at least 100M resources
- reduce number of concurrent running jobs

The orchestration process is implemented using a Durable Azure Function app written in Java.

![architecture diagram](./docs/architecture.drawio.png)

### Logical flow:
1. A user-defined external data extraction and transformation process creates NDJSON FHIR bundles and sends them to the Azure Storage Account specified by `FHIR_STORAGE_CONN_STR` and `FHIR_STORAGE_CONTAINER`.
1. When an export file is ready for processing, send a message to the Azure Storage Queue `FHIR_STORAGE_QUEUE`. 
`{ "filename": "2024-07-22.ndjson", "lineCount": 1059, "isLastFileInRequest": true}`
1. A Queue triggered Azure Function picks up the message and begins assembling an import batch. Additional files will be added to the same batch until the `MAX_BATCH_SIZE` is achieved.
1. When the Orchestration function detects that `MAX_BATCH_SIZE` has been exceeded, or the incoming Queue message contains `"isLastFileInRequest": true`, the batch is submitted to the $import endpoint of the FHIR API `FHIR_SERVER_URL`.
1. A Timer triggered function monitors the status endpoint URL returned by the $import endpoint.

### Logging and Monitoring
All batches and files are tracked in an Azure Storage Table `FHIR_STORAGE_TABLE`. When a job is complete, the status is updated in the table along with basic statistics about the job.

Application Insights and Log Analytics are used to feed an Azure Dashboard page that provides visibility into the running orchestration process and FHIR Import jobs.

Log files emitted from the FHIR $import process are automatically loaded to a storage container `fhirlogs` of the Storage Account defined by `FHIR_STORAGE_CONN_STR`. These logs can be consumed by Azure Fabric and PowerBI, or any other data/reporting/analysis tools, for the purpose of detailed status reporting. The setup and configuration of a Fabric workspace and PowerBI report are outside the scope of this repository.

## Local Development Setup Guide

1. Clone this repo to your local machine -OR- open in Github Codespaces.
1. Run `az login` AND `azd auth login` to authenticate with your Azure Subscription.
1. Run `azd up` OR `azd provision` to deploy the necessary Azure resources to your subscription.
1. Create a copy of [local.settings.example.json](./local.settings.example.json) and rename it to `local.settings.json`.
1. Update the local.settings.json file you just created with values from your environment using the variable guide below.
1. Run and debug by pressing `F5` in VSCode.

Azure Function Environment Variables

```JSON
    "FHIR_STORAGE_CONN_STR": "<fhir storage>",
    "FHIR_STORAGE_CONTAINER": "data",
    "FHIR_STORAGE_TABLE": "batchtable",
    "FHIR_STORAGE_QUEUE": "fhir-hose",
    "FHIR_SERVER_URL": "https://<fhirWorkspace>-<fhirApi>.fhir.azurehealthcareapis.com",
    "FHIR_IMPORT_MODE": "IncrementalLoad",
    "MAX_BATCH_SIZE": 100000000,
    "SUGGESTED_MIN_FILE_SIZE": 20000
```

## Contributing

This project welcomes contributions and suggestions.  Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.opensource.microsoft.com.

When you submit a pull request, a CLA bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., status check, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft 
trademarks or logos is subject to and must follow 
[Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/en-us/legal/intellectualproperty/trademarks/usage/general).
Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship.
Any use of third-party trademarks or logos are subject to those third-party's policies.
