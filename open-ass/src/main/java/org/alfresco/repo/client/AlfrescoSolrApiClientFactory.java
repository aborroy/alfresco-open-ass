package org.alfresco.repo.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;

/**
 * Factory class for creating and managing HTTP client connections to the Alfresco Solr API.
 * This class supports both secret-based and mutual TLS (mTLS) communication modes.
 * <p>
 * The factory initializes an {@link CloseableHttpClient} instance based on the communication mode
 * defined by the application properties.
 * </p>
 */
@Component
public class AlfrescoSolrApiClientFactory {

    /**
     * Header name for passing the Alfresco search secret to the Solr service.
     */
    public static final String X_ALFRESCO_SEARCH_SECRET = "X-Alfresco-Search-Secret";

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoSolrApiClientFactory.class);

    @Value("${content.service.url}")
    private String url;

    @Value("${content.solr.path}")
    private String apiPath;

    @Value("${content.solr.secret}")
    private String secret;

    @Value("${content.keystore.path}")
    private String keystorePath;

    @Value("${content.keystore.type}")
    private String keystoreType;

    @Value("${content.keystore.password}")
    private String keystorePassword;

    @Value("${content.truststore.path}")
    private String truststorePath;

    @Value("${content.truststore.type}")
    private String truststoreType;

    @Value("${content.truststore.password}")
    private String truststorePassword;

    @Value("${content.service.secureComms}")
    private String secureComms;

    private CloseableHttpClient httpClient;

    /**
     * Initializes the HTTP client depending on the secure communication mode (either "secret" or "https").
     * If the secureComms mode is not recognized, it throws a {@link RuntimeException}.
     */
    @PostConstruct
    public void init() {
        switch (secureComms) {
            case "secret":
                this.httpClient = createHttpClient();
                break;
            case "https":
                this.httpClient = createMtlsHttpClient();
                break;
            default:
                throw new IllegalArgumentException("Unsupported secureComms mode: " + secureComms + ". Use 'secret' or 'https'.");
        }
    }

    /**
     * Closes the {@link CloseableHttpClient} when the bean is destroyed.
     * Ensures that resources are properly cleaned up.
     */
    @PreDestroy
    public void close() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOG.error("Error closing HttpClient", e);
            }
        }
    }

    /**
     * Creates an HTTP client for secret-based communication mode.
     * Automatically adds the `X-Alfresco-Search-Secret` header to every outgoing request.
     *
     * @return an instance of {@link CloseableHttpClient}.
     */
    private CloseableHttpClient createHttpClient() {
        try {
            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create().build();

            return HttpClients.custom()
                    .setConnectionManager(cm)
                    .evictExpiredConnections()
                    .addRequestInterceptorFirst((request, entity, context) -> {
                        request.addHeader(X_ALFRESCO_SEARCH_SECRET, secret);
                    })
                    .build();
        } catch (Exception e) {
            LOG.error("Error creating HttpClient", e);
            return null;
        }
    }

    /**
     * Creates an HTTP client for mutual TLS (mTLS) communication mode.
     * Automatically adds the `X-Alfresco-Search-Secret` header to every outgoing request.
     *
     * @return an instance of {@link CloseableHttpClient}.
     */
    private CloseableHttpClient createMtlsHttpClient() {
        try {
            KeyStore keyStore = loadKeyStore(keystorePath, keystoreType, keystorePassword);
            KeyStore trustStore = loadKeyStore(truststorePath, truststoreType, truststorePassword);

            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                    .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                    .loadTrustMaterial(trustStore, null);

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContextBuilder.build());

            HttpClientConnectionManager cm = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(socketFactory)
                    .build();

            return HttpClients.custom()
                    .setConnectionManager(cm)
                    .evictExpiredConnections()
                    .build();
        } catch (Exception e) {
            LOG.error("Error creating mTLS HttpClient", e);
            return null;
        }
    }

    /**
     * Loads a {@link KeyStore} from the specified file path.
     *
     * @param path     the path to the keystore file.
     * @param type     the type of the keystore.
     * @param password the password for the keystore.
     * @return a loaded {@link KeyStore}.
     * @throws IOException if an error occurs while loading the keystore.
     */
    private KeyStore loadKeyStore(String path, String type, String password) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(path)) {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(inputStream, password.toCharArray());
            return keyStore;
        } catch (Exception e) {
            throw new IOException("Failed to load keystore: " + path, e);
        }
    }

    /**
     * Executes a GET request to the specified path and returns the response content.
     *
     * @param path the path to the Solr endpoint (relative to the base URL).
     * @return the response from the server as a String.
     * @throws IOException if an error occurs during request execution.
     */
    public String executeGetRequest(String path) throws IOException {
        HttpGet request = new HttpGet(url + apiPath + path);
        return httpClient.execute(request, response -> response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity()));
    }

    /**
     * Executes a POST request to the specified path with the given payload and returns the response content.
     *
     * @param path    the path to the Solr endpoint (relative to the base URL).
     * @param payload the payload to be sent in the POST request.
     * @return the response from the server as a String.
     * @throws IOException if an error occurs during request execution.
     */
    public String executePostRequest(String path, String payload) throws IOException {
        HttpPost request = new HttpPost(url + apiPath + path);
        request.setEntity(new StringEntity(payload, ContentType.APPLICATION_JSON));
        return httpClient.execute(request, response -> response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity()));
    }
}
