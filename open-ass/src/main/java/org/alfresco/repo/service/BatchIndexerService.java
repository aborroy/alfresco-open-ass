package org.alfresco.repo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.alfresco.opensearch.client.AlfrescoSolrApiClientFactory;
import org.alfresco.opensearch.index.Index;
import org.alfresco.opensearch.ingest.Indexer;
import org.alfresco.repo.service.beans.Node;
import org.alfresco.repo.service.beans.NodeContainer;
import org.alfresco.repo.service.beans.TransactionNodeContainer;
import org.alfresco.repo.service.beans.TransactionNode;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for batch indexing documents into OpenSearch.
 */
@Service
public class BatchIndexerService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchIndexerService.class);

    // Alfresco Content Model
    public static final String CM_NAME = "{http://www.alfresco.org/model/content/1.0}name";
    public static final String SYS_STORE_IDENTIFIER = "{http://www.alfresco.org/model/system/1.0}store-identifier";
    public static final String CONTENT = "{http://www.alfresco.org/model/content/1.0}content";
    public static final String SPACES_STORE = "SpacesStore";

    @Value("${batch.indexer.transaction.maxResults}")
    private int maxResults;

    @Value("${batch.indexer.indexableTypes}")
    private String indexableTypes;

    @Autowired
    private Indexer indexer;

    @Autowired
    private Index index;

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

    @PostConstruct
    public void initialize() throws Exception {
        index.createAlfrescoIndexes();
    }

    /**
     * Schedules the indexing process according to the cron expression specified in properties.
     */
    @Scheduled(cron = "${batch.indexer.cron}")
    public void index() throws Exception {

        long lastTransactionId = index.getAlfrescoIndexField() + 1;

        JsonNode rootNode = retrieveTransactions(lastTransactionId, maxResults);

        long minTxnId = lastTransactionId;
        long maxTxnId = lastTransactionId;

        JsonNode transactionsNode = rootNode.get("transactions");
        long maxTxnIdRepository = rootNode.get("maxTxnId").asLong();
        if (transactionsNode != null && transactionsNode.isArray() && !transactionsNode.isEmpty()) {
            for (JsonNode transactionNode : transactionsNode) {
                long id = transactionNode.get("id").asLong();
                minTxnId = Math.min(minTxnId, id);
                maxTxnId = Math.max(maxTxnId, id);
            }

            LOG.info("Indexing content for transactions between {} and {}", minTxnId, maxTxnId);
            processTransactions(minTxnId, maxTxnId);

            index.updateAlfrescoIndex(maxTxnId);
        } else {
            LOG.info(
                    """
                    All transactions have been indexed:
                     - maximum Transaction Id in Alfresco is {}
                     - maximum Transaction Id in OpenSearch is {}
                    """, maxTxnIdRepository, index.getAlfrescoIndexField());
        }
    }

    /**
     * Retrieves transactions from the Solr API.
     *
     * @param lastTransactionId the last transaction ID that was indexed
     * @param maxResults the maximum number of results to retrieve
     * @return a JSON node representing the transactions
     * @throws Exception if an error occurs during the API request
     */
    private JsonNode retrieveTransactions(long lastTransactionId, int maxResults) throws Exception {
        String endpoint = String.format("transactions?minTxnId=%d&maxResults=%d", lastTransactionId, maxResults);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readTree(alfrescoSolrApiClient.executeGetRequest(endpoint));
    }

    /**
     * Processes transactions between the specified minimum and maximum transaction IDs.
     *
     * @param minTxnId the minimum transaction ID
     * @param maxTxnId the maximum transaction ID
     * @throws Exception if an error occurs during processing
     */
    private void processTransactions(long minTxnId, long maxTxnId) throws Exception {
        String payload = String.format("{\"fromTxnId\": %d, \"toTxnId\": %d}", minTxnId, maxTxnId);
        String nodesResponse = alfrescoSolrApiClient.executePostRequest("nodes", payload);

        ObjectMapper objectMapper = new ObjectMapper();
        TransactionNodeContainer transactionNodeContainer = objectMapper.readValue(nodesResponse, TransactionNodeContainer.class);
        List<TransactionNode> transactionNodeList = transactionNodeContainer.getNodes();

        for (TransactionNode transactionNode : transactionNodeList) {
            processRawNode(transactionNode);
        }
    }

    /**
     * Processes an individual raw node.
     *
     * @param transactionNode the raw node to process
     * @throws Exception if an error occurs during processing
     */
    private void processRawNode(TransactionNode transactionNode) throws Exception {
        String payload = String.format("""
                {
                    "nodeIds": [%s],
                    "includeAclId": false,
                    "includeOwner": false,
                    "includePaths": false,
                    "includeParentAssociations": false,
                    "includeChildIds": false,
                    "includeChildAssociations": false
                }
                """, transactionNode.getId());
        String metadataResponse = alfrescoSolrApiClient.executePostRequest("metadata", payload);

        ObjectMapper objectMapper = new ObjectMapper();

        switch (transactionNode.getStatus()) {
            // Created or Updated
            case "u":
                NodeContainer nodeContainer = objectMapper.readValue(metadataResponse, NodeContainer.class);
                for (Node node : nodeContainer.getNodes()) {
                    if (isIndexableType(node.getType())) {
                        processNode(node);
                    }
                }
                break;
            // Deleted
            case "d":
                int index = transactionNode.getNodeRef().lastIndexOf("/");
                if (index == -1) {
                    throw new IllegalArgumentException("Invalid node reference: " + transactionNode.getNodeRef());
                }
                String uuid = transactionNode.getNodeRef().substring(index + 1);
                LOG.debug("Deleting document with NodeRef {}", transactionNode.getNodeRef());
                indexer.deleteDocument(uuid);
                break;
            default:
                throw new IllegalArgumentException("Unknown status: " + transactionNode.getStatus());
        }
    }

    /**
     * Checks if the node type is indexable based on the configured indexable types.
     *
     * @param type the node type
     * @return true if the type is indexable, false otherwise
     */
    private boolean isIndexableType(String type) {
        String[] types = indexableTypes.split(",");
        for (String t : types) {
            if (t.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processes an individual node, retrieving its content and indexing it.
     *
     * @param node the node to process
     * @throws Exception if an error occurs during processing
     */
    private void processNode(Node node) throws Exception {
        int index = node.getNodeRef().lastIndexOf("/");
        if (index == -1) {
            throw new IllegalArgumentException("Invalid node reference: " + node.getNodeRef());
        }
        String uuid = node.getNodeRef().substring(index + 1);
        String name = node.getProperties().get(CM_NAME).toString();
        String storeIdentifier = node.getProperties().get(SYS_STORE_IDENTIFIER).toString();
        String contentId = ((Map<?, ?>) node.getProperties().get(CONTENT)).get("contentId").toString();

        // Retrieve indexed contentId
        String contentIdInOS = indexer.getContentId(uuid);

        // Avoid processing nodes in ArchiveStore or VersionStore
        if (storeIdentifier.equals(SPACES_STORE)) {
            if (!contentId.equals(contentIdInOS)) {
                String content = alfrescoSolrApiClient.executeGetRequest("textContent?nodeId=" + node.getId());
                indexer.deleteDocumentIfExists(uuid);
                indexer.index(uuid, node.getId(), contentId, name, JsonUtils.escape(content));
            } else {
                LOG.debug("Un-indexed: ContentId for node {} has not changed {}", uuid, contentId);
            }
        }
    }

}