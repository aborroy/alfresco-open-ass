package org.alfresco.opensearch.client;

import org.apache.http.HttpHost;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Factory class for creating and managing OpenSearch clients.
 */
@Component
public class OpenSearchClientFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchClientFactory.class);

    @Value("${opensearch.host}")
    private String opensearchHost;

    @Value("${opensearch.port}")
    private Integer opensearchPort;

    @Value("${opensearch.protocol}")
    private String opensearchProtocol;

    @Value("${opensearch.client.keystore.path}")
    private String clientKeystorePath;

    @Value("${opensearch.client.keystore.password}")
    private String clientKeystorePassword;

    @Value("${opensearch.client.keystore.type}")
    private String clientKeystoreType;

    @Value("${opensearch.truststore.path}")
    private String trustStorePath;

    @Value("${opensearch.truststore.password}")
    private String trustStorePassword;

    @Value("${opensearch.truststore.type}")
    private String trustStoreType;

    private volatile OpenSearchClient openSearchClient;
    private volatile RestClient restClient;

    /**
     * Initializes the OpenSearch client and REST client.
     */
    private synchronized void init() {
        if (openSearchClient != null && restClient != null) {
            return;
        }

        try {
            HttpHost host = new HttpHost(opensearchHost, opensearchPort, opensearchProtocol);

            if (opensearchProtocol.equalsIgnoreCase("http")) {
                initializeHttpClient(host);
            } else if (opensearchProtocol.equalsIgnoreCase("https")) {
                initializeHttpsClient(host);
            } else {
                throw new IllegalArgumentException("Invalid OpenSearch protocol: " + opensearchProtocol);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize OpenSearch client", e);
            throw new RuntimeException("Failed to initialize OpenSearch client", e);
        }
    }

    /**
     * Initializes HTTP OpenSearch client.
     */
    private void initializeHttpClient(HttpHost hosts) {
        RestClientBuilder builder = RestClient.builder(hosts);
        restClient = builder.build();
        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        openSearchClient = new OpenSearchClient(transport);
        LOGGER.info("Initialized HTTP OpenSearch client for hosts: {}", (Object) hosts);
    }

    /**
     * Initializes HTTPS OpenSearch client with SSL context.
     */
    private void initializeHttpsClient(HttpHost hosts) throws Exception {
        SSLContext sslContext = createSSLContext();

        RestClientBuilder builder = RestClient.builder(hosts)
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(sslContext));

        restClient = builder.build();
        OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        openSearchClient = new OpenSearchClient(transport);
        LOGGER.info("Initialized HTTPS OpenSearch client for hosts: {}", (Object) hosts);
    }

    /**
     * Creates SSL context for HTTPS connections.
     */
    private SSLContext createSSLContext() throws Exception {
        try (FileInputStream keyStoreStream = new FileInputStream(clientKeystorePath);
             FileInputStream trustStoreStream = new FileInputStream(trustStorePath)) {

            KeyStore clientKeyStore = KeyStore.getInstance(clientKeystoreType);
            clientKeyStore.load(keyStoreStream, clientKeystorePassword.toCharArray());

            KeyStore trustStore = KeyStore.getInstance(trustStoreType);
            trustStore.load(trustStoreStream, trustStorePassword.toCharArray());

            return SSLContextBuilder.create()
                    .loadKeyMaterial(clientKeyStore, clientKeystorePassword.toCharArray())
                    .loadTrustMaterial(trustStore, null)
                    .build();
        }
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

    /**
     * Closes the RestClient when it's no longer needed.
     */
    public void close() {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (Exception e) {
                LOGGER.error("Error closing RestClient", e);
            }
        }
    }
}
