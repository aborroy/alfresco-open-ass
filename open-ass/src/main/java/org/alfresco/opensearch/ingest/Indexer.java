package org.alfresco.opensearch.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.OpenSearchClientFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.*;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.alfresco.opensearch.shared.OpenSearchConstants.CONTENT_ID;
import static org.opensearch.client.RequestOptions.DEFAULT;

/**
 * Component for handling the indexing of documents into OpenSearch.
 * It supports bulk indexing, document updates, and deletion of document segments in the index.
 */
@Component
public class Indexer {

    private static final Logger LOG = LoggerFactory.getLogger(Indexer.class);

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
     * Retrieves an instance of RestHighLevelClient from the factory.
     *
     * @return RestHighLevelClient instance
     */
    private RestHighLevelClient restHighLevelClient() {
        return openSearchClientFactory.getRestHighLevelClient();
    }

    /**
     * Indexes documents in bulk using a BulkRequest.
     * If there are failures during the indexing process, a RuntimeException is thrown.
     *
     * @param bulkRequest The request containing multiple documents to index.
     * @throws Exception If any failures occur during the bulk indexing.
     */
    public void index(BulkRequest bulkRequest) throws Exception {
        BulkResponse bulkResponse = restHighLevelClient().bulk(bulkRequest, DEFAULT);

        if (bulkResponse.hasFailures()) {
            for (BulkItemResponse bulkItemResponse : bulkResponse) {
                if (bulkItemResponse.isFailed()) {
                    LOG.error("OpenSearch indexing failure: {}", bulkItemResponse.getFailureMessage());
                    bulkRequest.requests().forEach(request -> LOG.error(request.toString()));
                    throw new RuntimeException(bulkItemResponse.getFailure().getCause());
                }
            }
        }
    }

    /**
     * Indexes or updates a document in OpenSearch with the provided UUID and content.
     * The content and content ID are stored in the OpenSearch index using a Painless script.
     *
     * @param uuid      The UUID of the document.
     * @param contentId The content ID of the document.
     * @param text      The text content to be indexed.
     */
    public void indexContent(String uuid, String contentId, String text) {
        if (!text.isEmpty()) {
            Request request = new Request("POST", "/" + indexName + "/_update/" + uuid);
            String jsonString = """
                    {
                        "script" : {
                            "source": "ctx._source['%s'] = params.text; ctx._source['%s'] = params.contentId;",
                            "lang": "painless",
                            "params" : {
                                "text": "%s",
                                "contentId": "%s"
                            }
                        }
                    }
                    """;
            String formattedJson = String.format(jsonString, "cm%3Acontent", CONTENT_ID, text, contentId);
            request.setEntity(new StringEntity(formattedJson, ContentType.APPLICATION_JSON));
            try {
                restClient().performRequest(request);
            } catch (Exception e) {
                LOG.warn("Document {} has not been updated due to the Exception: {}", uuid, e.getMessage());
                LOG.debug(e.getMessage(), e);
                LOG.warn(formattedJson);
            }
        }
    }

    /**
     * Retrieves the content ID for the specified document UUID from the OpenSearch index.
     *
     * @param uuid The UUID of the document to search for.
     * @return The content ID associated with the document.
     * @throws Exception If an error occurs while retrieving the content ID.
     */
    public String getContentId(String uuid) throws Exception {
        String contentId = "";

        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchQuery("_id", uuid));
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = restHighLevelClient().search(searchRequest, RequestOptions.DEFAULT);
        SearchHit[] searchHits = searchResponse.getHits().getHits();

        if (searchHits.length > 0) {
            contentId = (String) searchHits[0].getSourceAsMap().get(CONTENT_ID);
        }

        return contentId;
    }

    /**
     * Asynchronously deletes document segments from the index associated with the given UUID.
     * Retries the deletion up to 3 times with a 5-second delay between attempts to handle potential
     * concurrency issues.
     *
     * @param uuid The UUID of the document to delete.
     * @throws Exception If an error occurs during deletion.
     */
    public void deleteDocument(String uuid) throws Exception {
        CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            while (attempt < 3) {
                Request request = new Request("POST", "/" + indexName + "/_delete_by_query");
                String jsonString = """
                        {
                          "query": {
                            "match": {
                              "id": "%s"
                            }
                          }
                        }
                        """;
                request.setEntity(new StringEntity(String.format(jsonString, uuid), ContentType.APPLICATION_JSON));
                try {
                    Response response = restClient().performRequest(request);
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonResponse = objectMapper.readTree(response.getEntity().getContent());
                    int deleteCount = jsonResponse.get("total").asInt();
                    if (deleteCount > 0) {
                        return null;
                    }
                } catch (IOException e) {
                    LOG.warn(e.getMessage());
                }
                attempt++;
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        });
    }
}