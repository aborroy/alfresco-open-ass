package org.alfresco.opensearch.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.OpenSearchClientFactory;
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
 */
@Component
public class Index {

    static final Logger LOG = LoggerFactory.getLogger(Index.class);

    @Value("${opensearch.index.name}")
    private String indexName;

    @Autowired
    private OpenSearchClientFactory openSearchClientFactory;

    /**
     * Retrieves an instance of RestClient from the factory.
     *
     * @return RestClient instance
     */
    private RestClient restClient() {
        return openSearchClientFactory.getRestClient();
    }

    /**
     * Retrieves an instance of OpenSearchClient from the factory.
     *
     * @return OpenSearchClient instance
     */
    private OpenSearchClient openSearchClient() {
        return openSearchClientFactory.getOpenSearchClient();
    }

    /**
     * Create index to control alfresco indexing information
     */
    public void createAlfrescoIndex() throws Exception {

        if (!openSearchClient().indices().exists(new ExistsRequest.Builder().index("alfresco-control").build()).value()) {

            Request request = new Request("PUT", "/alfresco-control");
            String jsonString = """
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

            request.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
            restClient().performRequest(request);

            LOG.info("Internal index alfresco-control for alfresco indexing information has been created");
        }

        if (!openSearchClient().indices().exists(new ExistsRequest.Builder().index(indexName).build()).value()) {

            Request request = new Request("PUT", "/" + indexName);
            String jsonString = """
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
                    }
                    """;

            request.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
            restClient().performRequest(request);

            LOG.info("Internal index " + indexName + " for alfresco indexing information has been created");
        }

    }

    /**
     * Updates the last synchronization time in the Alfresco index.
     *
     * @param lastTransactionId The last transactionId to be updated in the Alfresco index.
     * @throws Exception If an error occurs during the update process.
     */
    public void updateAlfrescoIndex(Long lastTransactionId) throws Exception {
        Request request = new Request("PUT", "/alfresco-control/_doc/1");
        String jsonString = "{ \"lastTransactionId\": %d }".formatted(lastTransactionId);
        request.setEntity(new StringEntity(jsonString, ContentType.APPLICATION_JSON));
        restClient().performRequest(request);
    }

    /**
     * Retrieves the value of the last transaction Id synchronized from the Alfresco index.
     *
     * @return The value of the last transaction Id synchronized.
     * @throws Exception If an error occurs during the retrieval process.
     */
    public Long getAlfrescoIndexField() throws Exception {
        Request request = new Request("GET", "/alfresco-control/_doc/1");
        try {
            Response response = restClient().performRequest(request);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonResponse = objectMapper.readTree(response.getEntity().getContent());
            return jsonResponse.get("_source").get("lastTransactionId").asLong();
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return -1L;
            } else {
                throw e;
            }
        }

    }

    /**
     * Checks if the index exists in the OpenSearch cluster.
     *
     * @return true if the index exists, false otherwise
     * @throws IOException if an I/O error occurs
     */
    public boolean existIndex() throws IOException {
        return openSearchClient().indices().exists(new ExistsRequest.Builder().index(indexName).build()).value();
    }
}