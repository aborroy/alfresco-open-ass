package org.alfresco.opensearch.client;

import org.apache.http.HttpHost;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory class for creating and managing OpenSearch clients.
 */
@Component
public class OpenSearchClientFactory {

    @Value("${opensearch.host}")
    private String opensearchHost;

    @Value("${opensearch.port}")
    private Integer opensearchPort;

    @Value("${opensearch.protocol}")
    private String opensearchProtocol;

    private OpenSearchClient openSearchClient;
    private RestClient restClient;

    /**
     * Initializes the OpenSearch client and REST client.
     */
    private synchronized void init() {
        if (openSearchClient != null && restClient != null) {
            return;
        }

        RestClientBuilder builder = RestClient.builder(new HttpHost(opensearchHost, opensearchPort, opensearchProtocol));
        restClient = builder.build();

        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        openSearchClient = new OpenSearchClient(transport);
    }

    /**
     * Provides an instance of OpenSearchClient. Initializes the client if not already done.
     *
     * @return OpenSearchClient instance
     */
    public OpenSearchClient getOpenSearchClient() {
        if (openSearchClient == null) {
            init();
        }
        return openSearchClient;
    }

    /**
     * Provides an instance of RestClient. Initializes the client if not already done.
     *
     * @return RestClient instance
     */
    public RestClient getRestClient() {
        if (restClient == null) {
            init();
        }
        return restClient;
    }
}