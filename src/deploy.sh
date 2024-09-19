
# Function to display usage
usage() {
    echo "Usage: deploy.sh all|infra|function"
}

# Function to deploy infrastructure
deploy_infra() {
    echo "Deploying infra"
    az deployment sub create --subscription <subscriptionId> --location <location> --name java-azfunction-deploy --parameters ./iac/bicep/create-java-function-all.dev.bicepparam
}

# Function to package function app
package_function() {
    echo "Packaging function app"
    mvn clean package
    if [ $? -ne 0 ]; then
        echo "Failed to package function app"
    fi

    echo "Creating ZIP file"
    ZIP_FILE="target/functionapp.zip"
    cd ../target/azure-functions/<function-app-name>
    zip -r functionapp.zip *
    cd ../../../src
}

# Function to deploy function app
deploy_function() {
    echo "Deploying function app"
    az functionapp deployment source config-zip \
      --resource-group <resource-group-name> \
      --name <function-app-name> \
      --src target/azure-functions/<function-app-name>/functionapp.zip
    if [ $? -ne 0 ]; then
        echo "Failed to deploy function app"
    fi
}

if [ -z "$1" ]; then
    usage
fi

if [ "$1" == "all" ]; then
    echo "Deploying all"
    deploy_infra
    package_function
    deploy_function
elif [ "$1" == "infra" ]; then
    deploy_infra
elif [ "$1" == "function" ]; then
    package_function
    deploy_function
else
    usage
fi