# Alfresco OpenASS (Alfresco Open Alfresco Search Service)

**Alfresco OpenASS** is an open-source indexing service for Alfresco Community repositories that supports OpenSearch as indexing engine.

## How it works

![Architecture](docs/architecture.svg)

- **OpenSearch Compatibility:** Integrates seamlessly with OpenSearch 1.x and 2.x, allowing you to efficiently index and search content within Alfresco Community repositories.
- **Standalone and Decoupled Architecture:** OpenASS works as an independent service, separate from Alfresco's core architecture. It connects easily to standard Alfresco and OpenSearch deployments by leveraging the SOLR REST API in Alfresco and the standard REST API in OpenSearch.
- **Flexible Indexing and Mapping:** Provides robust configuration options for setting up custom indexing rules and mappings, enabling you to tailor search behavior to your specific content models.
- **Batch Processing for High-Volume Indexing:** Optimized for handling large repositories, OpenASS supports batch processing to enhance performance when indexing large volumes of content.

## Getting Started

### Prerequisites

Before setting up Alfresco OpenASS, ensure that you have the following:

- Alfresco Content Services (ACS) 7.x or higher
- OpenSearch 1.x or 2.x
- Java 17+
- Maven

### Usage

1. **Clone the Repository**:
   Begin by cloning the repository and navigating into the project directory:
   ```bash
   git clone https://github.com/aborroy/alfresco-open-ass.git
   cd alfresco-open-ass
   ```

2. **Configuration**:
   Update the `application.properties` file with your Alfresco and OpenSearch connection details. Below is a sample configuration:

    ```properties
    #---------------------------
    # OPENASS CONFIGURATION
    #---------------------------
    # Cron expression for synchronizing the OpenSearch index with the Alfresco repository
    batch.indexer.cron=0/12 * * * * ?
    # Maximum number of transactions to handle in a single batch
    batch.indexer.transaction.maxResults=100
    # Number of threads used for parallel content indexing
    batch.indexer.content.threads=4

    #---------------------------
    # ALFRESCO REPOSITORY CONFIGURATION
    #---------------------------
    # Alfresco server basic authentication credentials
    content.service.security.basicAuth.username=admin
    content.service.security.basicAuth.password=admin
    # Alfresco server URL and API path (e.g., http://localhost:8080 for HTTP or https://localhost:8443 for mTLS)
    content.service.url=http://localhost:8080
    content.service.path=/alfresco/api/-default-/public/alfresco/versions/1
    content.solr.path=/alfresco/service/api/solr/
    # Communication mode (secret or https)
    content.service.secureComms=secret
    # Secret mode configuration for SOLR
    content.solr.secret=i7wdvtrsfts
    # HTTPS mode configuration: Keystore settings
    content.keystore.path=
    content.keystore.type=
    content.keystore.password=
    # HTTPS mode configuration: Truststore settings
    content.truststore.path=
    content.truststore.type=
    content.truststore.password=

    #---------------------------
    # OPENSEARCH CONFIGURATION
    #---------------------------
    # OpenSearch server hostname
    opensearch.host=localhost
    # OpenSearch server port
    opensearch.port=9200
    # Protocol for communication with OpenSearch (http or https)
    opensearch.protocol=http
    # HTTPS mode configuration: Keystore settings
    opensearch.client.keystore.path=
    opensearch.client.keystore.password=
    opensearch.client.keystore.type=
    # HTTPS mode configuration: Truststore settings
    opensearch.truststore.path=
    opensearch.truststore.password=
    opensearch.truststore.type=
    # Automatically create the OpenSearch index if it doesn't exist
    opensearch.index.create=true
    # Name of the OpenSearch index
    opensearch.index.name=alfresco
    # Automatically create the OpenSearch control index if it doesn't exist
    opensearch.index.control.create=true
    # Name of the OpenSearch control index
    opensearch.index.control.name=alfresco-control
    ```

3. **Build the Project**:
   Build the project using Maven:
   ```bash
   mvn clean install
   ```

4. **Run the Service**:
   After ensuring that Alfresco and OpenSearch are running, you can start the OpenASS service:
   ```bash
   java -jar target/open-ass-0.8.0.jar
   ```

### Testing Scenarios

- **Alfresco Community (secret mode)**: [Testing for Alfresco Community (secret)](testing/community/alfresco)
  - OpenSearch is required: [Testing OpenSearch](testing/community/opensearch)
  
- **Alfresco Community (mTLS mode)**: [Testing for Alfresco Community (mTLS)](testing/community/alfresco-mtls)
  - OpenSearch is required: [Testing OpenSearch](testing/community/opensearch)

- **Alfresco Enterprise (secret mode)**: [Testing for Alfresco Enterprise](testing/enterprise)


## Contributing

Contributions are welcome! If you want to contribute, please fork the repository and submit a pull request. Make sure to follow the coding guidelines and document your changes thoroughly.

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Contact

For issues, feature requests, or questions, please open an issue on the [GitHub repository](https://github.com/aborroy/alfresco-open-ass/issues).

## Contributors

* Angel Borroy
* Miguel Sánchez
* Rodrigo Torres