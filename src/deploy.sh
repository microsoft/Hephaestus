#!/bin/bash

# Variables
SUBSCRIPTION_ID="<subscription-id>"
LOCATION="<location>"
RESOURCE_GROUP="<resource-group>"
FUNCTION_APP_NAME="<function-app-name>"
ARTIFACT_ID="<artifact-id-from-pom>"
FUNCTION_APP_PATH="../target/azure-functions/$ARTIFACT_ID"
ZIP_FILE="$FUNCTION_APP_PATH/functionapp.zip"

# Function to display usage
usage() {
    echo "Usage: deploy.sh all|infra|function"
}

# Function to deploy infrastructure
deploy_infra() {
    echo "Please run azd up to create/update the infra"
}

# Function to package function app
package_function() {
    echo "Packaging function app"
    cd ../
    mvn clean package
    if [ $? -ne 0 ]; then
        echo "Failed to package function app"
    fi
    cd src

    echo "Creating ZIP file"
    cd $FUNCTION_APP_PATH
    zip -r functionapp.zip *
    cd ../../../src
}

# Function to deploy function app
deploy_function() {
    echo "Deploying function app"
    az functionapp deployment source config-zip \
      --resource-group $RESOURCE_GROUP \
      --name $FUNCTION_APP_NAME \
      --src $ZIP_FILE
}

# Main script logic
if [ $# -eq 0 ]; then
    usage
fi

case $1 in
    all)
        deploy_infra
        package_function
        deploy_function
        ;;
    infra)
        deploy_infra
        ;;
    function)
        package_function
        deploy_function
        ;;
    *)
        usage
        ;;
esac