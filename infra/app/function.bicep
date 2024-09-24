param name string
param location string = resourceGroup().location
param tags object = {}

param allowedOrigins array = []
param applicationInsightsName string = ''
param appServicePlanId string
param appSettings object = {}
param keyVaultName string
param serviceName string = 'function'
param storageAccountName string
param managedIdentity bool = !empty(keyVaultName)
param fhirStorageName string

resource fhirStorage 'Microsoft.Storage/storageAccounts@2021-09-01' existing = {
  name: fhirStorageName
}

module api '../core/host/functions.bicep' = {
  name: '${serviceName}-functions-java-module'
  params: {
    name: name
    location: location
    tags: union(tags, { 'azd-service-name': serviceName })
    allowedOrigins: allowedOrigins
    alwaysOn: false
    appSettings: union(appSettings, {
      FHIR_STORAGE_CONN_STR: 'DefaultEndpointsProtocol=https;AccountName=${fhirStorage.name};AccountKey=${fhirStorage.listKeys().keys[0].value};EndpointSuffix=${environment().suffixes.storage}'
    })
    applicationInsightsName: applicationInsightsName
    appServicePlanId: appServicePlanId
    keyVaultName: keyVaultName
    managedIdentity: managedIdentity
    storageAccountName: storageAccountName
    scmDoBuildDuringDeployment: false
    // java
    runtimeName: 'java'
    runtimeVersion: '17'
  }
}

output SERVICE_API_IDENTITY_PRINCIPAL_ID string = api.outputs.identityPrincipalId
output SERVICE_API_NAME string = api.outputs.name
output SERVICE_API_URI string = api.outputs.uri
