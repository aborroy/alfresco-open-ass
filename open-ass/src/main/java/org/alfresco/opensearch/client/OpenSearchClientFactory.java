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

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

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

    private OpenSearchClient openSearchClient;
    private RestClient restClient;

    /**
     * Initializes the OpenSearch client and REST client.
     */
    private synchronized void init() {
        if (openSearchClient != null && restClient != null) {
            return;
        }

        if (opensearchProtocol.equals("http")) {

            RestClientBuilder builder = RestClient.builder(new HttpHost(opensearchHost, opensearchPort, opensearchProtocol));
            restClient = builder.build();

            OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            openSearchClient = new OpenSearchClient(transport);

        } else if (opensearchProtocol.equals("https")) {

            try {

                KeyStore clientKeyStore = KeyStore.getInstance(clientKeystoreType);
                clientKeyStore.load(new FileInputStream(clientKeystorePath), clientKeystorePassword.toCharArray());

                KeyStore trustStore = KeyStore.getInstance(trustStoreType);
                trustStore.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray());

                SSLContext sslContext = SSLContextBuilder.create()
                        .loadKeyMaterial(clientKeyStore, clientKeystorePassword.toCharArray())
                        .loadTrustMaterial(trustStore, null)
                        .build();

                RestClientBuilder builder = RestClient.builder(new HttpHost(opensearchHost, opensearchPort, opensearchProtocol))
                        .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setSSLContext(sslContext));

                restClient = builder.build();

                OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
                openSearchClient = new OpenSearchClient(transport);

            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize OpenSearch client", e);
            }

        } else {
            throw new RuntimeException("Communication protocol with OpenSearch " + opensearchProtocol +
                    ", specified in opensearch.protocol is not valid. Expecting http or https.");
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
}