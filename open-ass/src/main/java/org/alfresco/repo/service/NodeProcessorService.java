package org.alfresco.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.AlfrescoSolrApiClientFactory;
import org.alfresco.opensearch.index.OpenSearchRequestBuilder;
import org.alfresco.opensearch.ingest.Indexer;
import org.alfresco.repo.service.beans.*;
import org.alfresco.repo.service.content.NodeContentProcessor;
import org.opensearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.alfresco.utils.NodeUtils.extractUuidFromNodeRef;

/**
 * Service for processing nodes and transactions using the Alfresco Solr API and OpenSearch.
 * This class handles the retrieval of transactions, processing of node metadata,
 * indexing of nodes, and deletion of nodes from the index.
 */
@Service
public class NodeProcessorService {

    private static final Logger LOG = LoggerFactory.getLogger(NodeProcessorService.class);

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

    @Autowired
    private OpenSearchRequestBuilder openSearchRequestBuilder;

    @Autowired
    private Indexer indexer;

    @Autowired
    private NodeContentProcessor nodeContentProcessor;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Retrieves transactions from the Solr API using the specified parameters.
     *
     * @param lastTransactionId the ID of the last indexed transaction
     * @param maxResults        the maximum number of transactions to retrieve
     * @return a {@link TransactionContainer} containing the retrieved transactions
     * @throws Exception if an error occurs during the API request
     */
    public TransactionContainer retrieveTransactions(long lastTransactionId, int maxResults) throws Exception {
        String endpoint = String.format("transactions?minTxnId=%d&maxResults=%d", lastTransactionId, maxResults);
        LOG.debug("Sending request to retrieve transactions starting from ID {} with maxResults {}", lastTransactionId, maxResults);

        TransactionContainer transactions = objectMapper.readValue(alfrescoSolrApiClient.executeGetRequest(endpoint), TransactionContainer.class);
        LOG.debug("Retrieved {} transactions starting from ID {}", transactions.getTransactions().size(), lastTransactionId);

        return transactions;
    }

    /**
     * Processes transactions between the specified minimum and maximum transaction IDs.
     *
     * @param minTxnId           the minimum transaction ID to process
     * @param maxTxnId           the maximum transaction ID to process
     * @param maxTxnCommitTime   the maximum commit time for the transaction list
     * @throws Exception if an error occurs during transaction processing
     */
    public void processTransactions(long minTxnId, long maxTxnId, long maxTxnCommitTime) throws Exception {
        String payload = String.format("{\"fromTxnId\": %d, \"toTxnId\": %d}", minTxnId, maxTxnId);
        LOG.debug("Requesting transaction nodes between IDs {} and {}", minTxnId, maxTxnId);

        String nodesResponse = alfrescoSolrApiClient.executePostRequest("nodes", payload);
        TransactionNodeContainer transactionNodeContainer = objectMapper.readValue(nodesResponse, TransactionNodeContainer.class);
        List<TransactionNode> transactionNodeList = transactionNodeContainer.getNodes();

        LOG.debug("Processing {} transaction nodes between IDs {} and {}", transactionNodeList.size(), minTxnId, maxTxnId);

        for (TransactionNode transactionNode : transactionNodeList) {
            LOG.debug("Processing node with ID {} and status {}", transactionNode.getId(), transactionNode.getStatus());
            processRawNode(transactionNode, maxTxnCommitTime);
        }
    }

    /**
     * Processes an individual raw node, handling metadata retrieval and indexing.
     *
     * @param transactionNode     the raw node to process
     * @param maxTxnCommitTime    the maximum commit time for the transaction list
     * @throws Exception if an error occurs during processing
     */
    private void processRawNode(TransactionNode transactionNode, long maxTxnCommitTime) throws Exception {
        String payload = createMetadataRequestPayload(transactionNode.getId());
        LOG.debug("Requesting metadata for node ID {}", transactionNode.getId());

        String metadataResponse = alfrescoSolrApiClient.executePostRequest("metadata", payload);
        LOG.debug("Received metadata for node ID {}", transactionNode.getId());

        switch (transactionNode.getStatus()) {
            case "u":
                LOG.debug("Processing updated node with ID {}", transactionNode.getId());
                processUpdatedNode(metadataResponse, maxTxnCommitTime);
                break;

            case "d":
                LOG.debug("Processing deleted node with ID {}", transactionNode.getId());
                processDeletedNode(transactionNode);
                break;

            default:
                LOG.error("Unknown status: {}", transactionNode.getStatus());
                throw new IllegalArgumentException("Unknown status: " + transactionNode.getStatus());
        }
    }

    /**
     * Creates the metadata request payload for a given node ID.
     *
     * @param nodeId the ID of the node
     * @return the metadata request payload as a JSON string
     */
    private String createMetadataRequestPayload(long nodeId) {
        LOG.debug("Creating metadata request payload for node ID {}", nodeId);
        return String.format("""
                {
                    "nodeIds": [%d],
                    "includeAclId": true,
                    "includeOwner": true,
                    "includePaths": true,
                    "includeParentAssociations": true,
                    "includeChildIds": false,
                    "includeChildAssociations": false
                }
                """, nodeId);
    }

    /**
     * Processes an updated node by retrieving its metadata and indexing it.
     *
     * @param metadataResponse     the response containing the node metadata
     * @param maxTxnCommitTime    the maximum commit time for the transaction list
     * @throws Exception if an error occurs during processing
     */
    private void processUpdatedNode(String metadataResponse, long maxTxnCommitTime) throws Exception {
        NodeContainer nodeContainer = objectMapper.readValue(metadataResponse, NodeContainer.class);
        LOG.debug("Processing updated node. Metadata received for {} nodes", nodeContainer.getNodes().size());

        Set<Integer> uniqueAclIds = nodeContainer.getNodes().stream()
                .map(Node::getAclId)
                .collect(Collectors.toSet());
        LOG.debug("Fetching ACL readers for {} unique ACL IDs", uniqueAclIds.size());

        String aclResponse = fetchAclReaders(uniqueAclIds);
        Map<Integer, List<String>> aclIdToReadersMap = mapAclReaders(aclResponse);

        setReadersForNodes(nodeContainer, aclIdToReadersMap);
        BulkRequest bulkRequest = openSearchRequestBuilder.buildBulkRequest(nodeContainer.getNodes(), maxTxnCommitTime);
        LOG.debug("Indexing {} nodes in bulk", nodeContainer.getNodes().size());
        indexer.index(bulkRequest);

        // Update content for indexed nodes asynchronously
        nodeContentProcessor.processNodeContentAsync(nodeContainer.getNodes());
    }

    /**
     * Fetches ACL readers for the given ACL IDs.
     *
     * @param uniqueAclIds the set of unique ACL IDs
     * @return the response containing ACL readers as a JSON string
     * @throws Exception if an error occurs during the request
     */
    private String fetchAclReaders(Set<Integer> uniqueAclIds) throws Exception {
        LOG.debug("Fetching ACL readers for ACL IDs: {}", uniqueAclIds);
        String aclPayload = objectMapper.writeValueAsString(Map.of("aclIds", uniqueAclIds));
        return alfrescoSolrApiClient.executePostRequest("aclsReaders", aclPayload);
    }

    /**
     * Maps ACL readers from the response into a map of ACL IDs to their corresponding readers.
     *
     * @param aclResponse the response containing ACL readers as a JSON string
     * @return a map of ACL ID to list of readers
     * @throws Exception if an error occurs during parsing
     */
    private Map<Integer, List<String>> mapAclReaders(String aclResponse) throws Exception {
        AclReadersResponse aclReadersResponse = objectMapper.readValue(aclResponse, AclReadersResponse.class);
        LOG.debug("Mapped ACL readers for {} ACL IDs", aclReadersResponse.getAclsReaders().size());
        return aclReadersResponse.getAclsReaders().stream()
                .collect(Collectors.toMap(AclReader::getAclId, AclReader::getReaders));
    }

    /**
     * Sets readers for each node and logs the action.
     *
     * @param nodeContainer           the container holding nodes to process
     * @param aclIdToReadersMap      the map of ACL ID to list of readers
     */
    private void setReadersForNodes(NodeContainer nodeContainer, Map<Integer, List<String>> aclIdToReadersMap) {
        for (Node node : nodeContainer.getNodes()) {
            List<String> readers = aclIdToReadersMap.get(node.getAclId());
            node.setReaders(readers);
            LOG.debug("Set readers for node ID {}: {}", node.getId(), readers);
        }
    }

    /**
     * Processes a deleted node by removing it from the index.
     *
     * @param transactionNode the node to delete
     * @throws Exception if an error occurs during deletion
     */
    private void processDeletedNode(TransactionNode transactionNode) throws Exception {
        String uuid = extractUuidFromNodeRef(transactionNode.getNodeRef());
        LOG.debug("Deleting document with NodeRef {}", transactionNode.getNodeRef());
        indexer.deleteDocument(uuid);
    }
}