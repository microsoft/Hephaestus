param workspaceName string = ''
param location string = resourceGroup().location
param tags object

resource healthDataService 'Microsoft.HealthcareApis/workspaces@2024-03-31' = {
  name: workspaceName
  location: location
  tags: tags
}

resource fhirService 'Microsoft.HealthcareApis/workspaces/fhirservices@2024-03-31' = {
  parent: healthDataService
  name: 'api1'
  location: location
  kind: 'fhir-R4'
  properties: {    
    authenticationConfiguration: {
      authority: '${environment().authentication.loginEndpoint}/${environment().authentication.tenant}'
      audience: 'https://${workspaceName}-api1.fhir.azurehealthcareapis.com'
    }
  }
}
