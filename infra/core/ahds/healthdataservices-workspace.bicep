param workspaceName string = ''
param location string = resourceGroup().location
param tags object

resource healthDataService 'Microsoft.HealthcareApis/workspaces@2024-03-31' = {
  name: workspaceName
  location: location
  tags: tags
  properties: {
    publicNetworkAccess: 'Enabled'
  }
}

resource fhirService 'Microsoft.HealthcareApis/workspaces/fhirservices@2024-03-31' = {
  parent: healthDataService
  name: 'api1'
  location: location
  kind: 'fhir-R4'
  properties: {    
    authenticationConfiguration: {
      authority: '${environment().authentication.loginEndpoint}${tenant().tenantId}'
      audience: 'https://${workspaceName}-api1.fhir.azurehealthcareapis.com'
      smartProxyEnabled: false
    }
    publicNetworkAccess: 'Enabled'
  }
}

// bicep to provision a new healthcareapis workspace and fhir service

output healthDataServiceName string = healthDataService.name
