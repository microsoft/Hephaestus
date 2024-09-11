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

var abbrs = loadJsonContent('abbreviations.json')
var resourceToken = toLower(uniqueString(subscription().id, environmentName, location))
var tags = { 'azd-env-name': environmentName }

resource resourceGroup 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: !empty(resourceGroupName) ? resourceGroupName : '${abbrs.resourcesResourceGroups}${environmentName}'
  location: location
  tags: tags
}

module healthdataservice 'core/ahds/healthdataservices-workspace.bicep' = {
  scope: resourceGroup
  name: 'healthcareapis'
  params: {
    workspaceName: !empty(healthDataServiceWorkspaceName) ? healthDataServiceWorkspaceName : '${abbrs.healthcareapis}${resourceToken}'
    location: location
    tags: tags
  }
}

var appServiceName = !empty(functionAppName) ? functionAppName : '${abbrs.webSitesFunctions}-${resourceToken}'
var storageAccountName = !empty(funcStorageAccountName) ? funcStorageAccountName : '${abbrs.storageStorageAccounts}${resourceToken}'

var containers = [
  {
    name: 'data'
    publicAccess: 'None'
  }
]

var queues = [
  {
    name: 'batchnotification'
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
  }
}
