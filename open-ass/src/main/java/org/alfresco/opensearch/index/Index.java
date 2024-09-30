package org.alfresco.opensearch.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.OpenSearchClientFactory;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.RestClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.ExistsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Component for managing OpenSearch indices.
 * This class provides methods for creating and managing Alfresco indexes in OpenSearch.
 */
@Component
public class Index {

    private static final Logger LOG = LoggerFactory.getLogger(Index.class);

    @Value("${opensearch.index.create}")
    private Boolean indexCreation;

    @Value("${opensearch.index.name}")
    private String indexName;

    @Value("${opensearch.index.control.create}")
    private Boolean indexControlCreation;

    @Value("${opensearch.index.control.name}")
    private String indexControlName;

    @Autowired
    private OpenSearchClientFactory openSearchClientFactory;

    /**
     * Retrieves an instance of RestClient from the factory.
     *
     * @return RestClient instance
     */
    private RestClient getRestClient() {
        return openSearchClientFactory.getRestClient();
    }

    /**
     * Retrieves an instance of OpenSearchClient from the factory.
     *
     * @return OpenSearchClient instance
     */
    private OpenSearchClient getOpenSearchClient() {
        return openSearchClientFactory.getOpenSearchClient();
    }

    /**
     * Creates the necessary Alfresco indexes in OpenSearch if they do not already exist.
     *
     * @throws Exception If an error occurs during index creation.
     */
    public void createAlfrescoIndexes() throws Exception {
        OpenSearchClient openSearchClient = getOpenSearchClient();
        RestClient restClient = getRestClient();

        // Create control index if enabled and does not exist
        if (indexControlCreation && !openSearchClient.indices().exists(new ExistsRequest.Builder().index(indexControlName).build()).value()) {
            String controlIndexMapping = """
                    {
                      "mappings": {
                        "properties": {
                          "lastTransactionId": {
                            "type": "long"
                          }
                        }
                      }
                    }
                    """;
            createIndex(restClient, indexControlName, controlIndexMapping);
            LOG.info("Internal index '{}' for alfresco indexing information has been created", indexControlName);
        }

        // Create main Alfresco index if enabled and does not exist
        if (indexCreation && !openSearchClient.indices().exists(new ExistsRequest.Builder().index(indexName).build()).value()) {
            String alfrescoIndexMapping = """
                    {
                        "mappings": {
                             "properties": {
                               "id": {
                                 "type": "text"
                               },
                               "dbid": {
                                   "type": "long"
                               },
                               "contentId": {
                                   "type": "long"
                               },
                               "name": {
                                 "type": "text"
                               },
                               "text": {
                                 "type": "text"
                               }
                             }
                           }
                         }
                    """;
            createIndex(restClient, indexName, alfrescoIndexMapping);
            LOG.info("Internal index '{}' for alfresco indexing information has been created", indexName);
        }

    }

    /**
     * Creates an index with the specified name and mapping in OpenSearch.
     *
     * @param restClient   The RestClient instance to use.
     * @param indexName    The name of the index to create.
     * @param mappingJson  The JSON string for the index mapping.
     * @throws IOException If an error occurs during index creation.
     */
    private void createIndex(RestClient restClient, String indexName, String mappingJson) throws IOException {
        Request request = new Request("PUT", "/" + indexName);
        request.setEntity(new StringEntity(mappingJson, ContentType.APPLICATION_JSON));
        restClient.performRequest(request);
    }

    /**
     * Updates the last transactionId in the Alfresco control index.
     *
     * @param lastTransactionId The last transactionId to be updated.
     * @throws IOException If an error occurs during the update process.
     */
    public void updateAlfrescoControlIndexStatus(Long lastTransactionId) throws IOException {
        Request request = new Request("PUT", "/" + indexControlName + "/_doc/1");
        String jsonString = String.format("{ \"lastTransactionId\": %d }", lastTransactionId);
        request.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
        getRestClient().performRequest(request);
    }

    /**
     * Retrieves the last transactionId synchronized from the Alfresco control index.
     *
     * @return The last synchronized transactionId, 0 if not found (404), or -1 for other errors.
     * @throws IOException If an error occurs during retrieval.
     */
    public Long getAlfrescoControlIndexStatus() throws IOException {
        Request request = new Request("GET", "/" + indexControlName + "/_doc/1");
        try {
            Response response = getRestClient().performRequest(request);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.getEntity().getContent());
            return jsonResponse.get("_source").get("lastTransactionId").asLong();
        } catch (ResponseException e) {
            int statusCode = e.getResponse().getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return 0L;
            } else {
                throw new IOException("Unexpected response status: " + statusCode, e);
            }
        }
    }


    /**
     * Checks if the specified index exists in the OpenSearch cluster.
     *
     * @return true if the index exists, false otherwise.
     * @throws IOException If an error occurs during the existence check.
     */
    public boolean existIndex() throws IOException {
        return getOpenSearchClient().indices().exists(new ExistsRequest.Builder().index(indexName).build()).value();
    }

}
