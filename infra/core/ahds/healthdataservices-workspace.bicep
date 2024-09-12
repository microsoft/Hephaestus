param workspaceName string = ''
param location string = resourceGroup().location
param tags object
param fhirApiName string = 'api1'
param importConfiguration object = {}

resource healthDataService 'Microsoft.HealthcareApis/workspaces@2024-03-31' = {
  name: workspaceName
  location: location
  tags: tags
  properties: {
    publicNetworkAccess: 'Enabled'
  }
}


var fhirServiceUrl = 'https://${workspaceName}-${fhirApiName}.fhir.azurehealthcareapis.com'

resource fhirService 'Microsoft.HealthcareApis/workspaces/fhirservices@2024-03-31' = {
  parent: healthDataService
  name: fhirApiName
  location: location
  kind: 'fhir-R4'
  properties: {    
    authenticationConfiguration: {
      authority: '${environment().authentication.loginEndpoint}${tenant().tenantId}'
      audience: fhirServiceUrl
      smartProxyEnabled: false
    }    
    publicNetworkAccess: 'Enabled'
    importConfiguration: importConfiguration
  }
  identity: {
    type: 'SystemAssigned'
  }
}

// bicep to provision a new healthcareapis workspace and fhir service

output HEALTH_DATA_SERVICE_WORKSPACE_NAME string = healthDataService.name
output FHIR_SERVICE_URL string = fhirServiceUrl
output FHIR_SERVICE_IDENTITY_ID string = fhirService.identity.principalId
