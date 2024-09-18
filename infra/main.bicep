targetScope = 'subscription'

@minLength(1)
@maxLength(64)
@description('Name of the the environment which is used to generate a short unique hash used in all resources.')
param environmentName string

@minLength(1)
@description('Primary location for all resources')
@allowed(['southcentralus','northeurope','westeurope','eastus','eastus2','australiaeast','uksouth','westus2','canadacentral','switzerlandnorth','westus3','centralindia','southeastasia','koreacentral','swedencentral','northcentralus','francecentral','qatarcentral','japaneast','westcentralus','germanywestcentral'])
param location string

param healthDataServiceWorkspaceName string = ''

param resourceGroupName string = ''

param appServicePlanName string = ''
param functionAppName string = ''
param funcStorageAccountName string = ''
param fhirStorageContainerName string = 'data'
param fhirStorageQueueName string = 'batchready'
param fhirStorageTableName string = 'batchtable'

var abbrs = loadJsonContent('abbreviations.json')
var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = { 'azd-env-name': environmentName }

resource resourceGroup 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: !empty(resourceGroupName) ? resourceGroupName : '${abbrs.resourcesResourceGroups}${environmentName}'
  location: location
  tags: tags
}

var importConfiguration = {
  enabled: true
  initialImportMode: false
  integrationDataStore: storageAccount.outputs.name
}

module healthdataservice 'core/ahds/healthdataservices-workspace.bicep' = {
  scope: resourceGroup
  name: 'healthcareapis'
  params: {
    workspaceName: !empty(healthDataServiceWorkspaceName) ? healthDataServiceWorkspaceName : '${abbrs.healthcareapis}${resourceToken}'
    location: location
    tags: tags
    importConfiguration: importConfiguration
  }
}

var appServiceName = !empty(functionAppName) ? functionAppName : '${abbrs.webSitesFunctions}-${resourceToken}'
var storageAccountName = !empty(funcStorageAccountName) ? funcStorageAccountName : '${abbrs.storageStorageAccounts}${resourceToken}'

var containers = [
  {
    name: fhirStorageContainerName
    publicAccess: 'None'
  }
]

var queues = [
  {
    name: fhirStorageQueueName
  }
]

var tables = [
  {
    name: fhirStorageTableName
  }
]

module storageAccount 'core/storage/storage-account.bicep' = {
  scope: resourceGroup
  name: 'storage'
  params: {
    name: storageAccountName
    location: location
    tags: tags
    containers: containers
    queues: queues
    tables: tables
  }
}

module appServicePlan 'core/host/appserviceplan.bicep' = {
  name: 'appserviceplan'
  scope: resourceGroup
  params: {
    name: !empty(appServicePlanName) ? appServicePlanName : '${abbrs.webServerFarms}${resourceToken}'
    location: location
    tags: tags
    sku: {
      name: 'Y1'
      tier: 'Dynamic'
    }
  }
}

module logAnalytics 'core/monitor/loganalytics.bicep' = {
  scope: resourceGroup
  name: 'loganalytics'
  params: {
    name: 'logAnalyticsWorkspace'
    location: location
    tags: tags
  }
}

module appInsights 'core/monitor/applicationinsights.bicep' = {
  scope: resourceGroup
  name: 'appInsights'
  params: {
    name: 'appInsightsInstance'
    location: location
    tags: tags
    logAnalyticsWorkspaceId: logAnalytics.outputs.id
  }
}

module functionApp 'app/function.bicep' = {
  scope: resourceGroup
  name: 'functionapp'
  params: {
    name: appServiceName
    location: location
    tags: tags
    storageAccountName: storageAccount.outputs.name
    keyVaultName: ''
    appServicePlanId: appServicePlan.outputs.id
    managedIdentity: true
    appSettings: {
      FHIR_SERVER_URL: healthdataservice.outputs.FHIR_SERVICE_URL
      FHIR_STORAGE_CONTAINER: fhirStorageContainerName
      FHIR_STORAGE_QUEUE: fhirStorageQueueName
      FHIR_STORAGE_TABLE: fhirStorageTableName
      APPINSIGHTS_INSTRUMENTATIONKEY: appInsights.outputs.connectionString
    }
  }
}

// Add dashboard
module dashboard 'core/monitor/logging-dashboard.bicep' = {
  scope: resourceGroup
  name: 'dashboard'
  params: {
    name: 'loggingDashboard'
    applicationInsightsName: appInsights.outputs.name
    location: location
    functionAppName: appServiceName
  }
}

// Role assignments

module storageBlobDataRoleAssignment 'core/security/role.bicep' = {
  name: 'storageblobroleassignment'
  scope: resourceGroup
  params: {
    principalId: healthdataservice.outputs.FHIR_SERVICE_IDENTITY_ID
    roleDefinitionId: 'ba92f5b4-2d11-453d-a403-e96b0029c9fe' // Storage Blob Data Contributor
  }
}

// Function App Role Assignments
module functionAppFhirRoleAssignment 'core/security/role.bicep' = {
  name: 'functionfhirroleassignment'
  scope: resourceGroup
  params: {
    principalId: functionApp.outputs.SERVICE_API_IDENTITY_PRINCIPAL_ID
    roleDefinitionId: '4465e953-8ced-4406-a58e-0f6e3f3b530b' // FHIR Data Importer
  }
}

module functionAppStorageBlobRoleAssignment 'core/security/role.bicep' = {
  name: 'functionstorageblobroleassignment'
  scope: resourceGroup
  params: {
    principalId: functionApp.outputs.SERVICE_API_IDENTITY_PRINCIPAL_ID
    roleDefinitionId: '2a2b9908-6ea1-4ae2-8e65-a410df84e7d1' // Storage Blob Data Reader
  }
}

module functionAppStorageTableRoleAssignment 'core/security/role.bicep' = {
  name: 'functionstoragetableroleassignment'
  scope: resourceGroup
  params: {
    principalId: functionApp.outputs.SERVICE_API_IDENTITY_PRINCIPAL_ID
    roleDefinitionId: '0a9a7e1f-b9d0-4cc4-a60d-0319b160aaa3' // Storage Table Data Contributor
  }
}

module functionAppStorageQueueRoleAssignment 'core/security/role.bicep' = {
  name: 'functionstoragequeueroleassignment'
  scope: resourceGroup
  params: {
    principalId: functionApp.outputs.SERVICE_API_IDENTITY_PRINCIPAL_ID
    roleDefinitionId: '974c5e8b-45b9-4653-ba55-5f855dd0fb88' // Storage Queue Data Contributor
  }
}
