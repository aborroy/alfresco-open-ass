package org.alfresco.indexer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.index.OpenSearchRequestBuilder;
import org.alfresco.opensearch.ingest.Indexer;
import org.alfresco.repo.index.AlfrescoService;
import org.alfresco.repo.index.beans.*;
import org.opensearch.action.bulk.BulkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static org.alfresco.utils.NodeUtils.extractUuidFromNodeRef;

/**
 * Service responsible for processing nodes and transactions using Alfresco's Solr API and OpenSearch.
 * This service handles the retrieval of transactions, processing of node metadata,
 * indexing of nodes, and deletion of nodes from the OpenSearch index.
 */
@Service
public class NodeProcessorIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(NodeProcessorIndexer.class);

    @Autowired
    private AlfrescoService alfrescoService;

    @Autowired
    private OpenSearchRequestBuilder openSearchRequestBuilder;

    @Autowired
    private Indexer indexer;

    @Autowired
    private NodeContentProcessor nodeContentProcessor;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Processes a range of transactions based on their transaction IDs.
     * Retrieves the nodes from each transaction and processes them based on their status (update/delete).
     *
     * @param minTxnId         the minimum transaction ID to process
     * @param maxTxnId         the maximum transaction ID to process
     * @param maxTxnCommitTime the maximum commit time to process up to
     * @throws Exception if an error occurs during transaction processing
     */
    public void processTransactions(long minTxnId, long maxTxnId, long maxTxnCommitTime) throws Exception {
        TransactionNodeContainer transactionNodeContainer = alfrescoService.getTransactionNodeContainer(minTxnId, maxTxnId);
        List<TransactionNode> transactionNodeList = transactionNodeContainer.getNodes();

        LOG.debug("Processing {} transaction nodes between IDs {} and {}", transactionNodeList.size(), minTxnId, maxTxnId);

        for (TransactionNode transactionNode : transactionNodeList) {
            LOG.debug("Processing node with ID {} and status {}", transactionNode.getId(), transactionNode.getStatus());
            processRawNode(transactionNode, maxTxnCommitTime);
        }
    }

    /**
     * Processes an individual raw node by retrieving its metadata and handling its indexing or deletion
     * based on the node's status.
     *
     * @param transactionNode  the transaction node to process
     * @param maxTxnCommitTime the maximum commit time for processing the node
     * @throws Exception if an error occurs during node processing
     */
    private void processRawNode(TransactionNode transactionNode, long maxTxnCommitTime) throws Exception {
        NodeContainer nodeContainer = alfrescoService.getMetadata(transactionNode.getId());
        LOG.debug("Received metadata for node ID {}", transactionNode.getId());

        switch (transactionNode.getStatus()) {
            case "u":  // Updated node
                LOG.debug("Processing updated node with ID {}", transactionNode.getId());
                processUpdatedNode(nodeContainer, maxTxnCommitTime);
                break;

            case "d":  // Deleted node
                LOG.debug("Processing deleted node with ID {}", transactionNode.getId());
                processDeletedNode(transactionNode);
                break;

            default:
                LOG.error("Unknown status '{}' for node with ID {}", transactionNode.getStatus(), transactionNode.getId());
                throw new IllegalArgumentException("Unknown status: " + transactionNode.getStatus());
        }
    }

    /**
     * Processes an updated node by setting the appropriate readers based on ACL IDs and indexing the node.
     *
     * @param nodeContainer    the container holding node metadata
     * @param maxTxnCommitTime the maximum commit time for processing the node
     * @throws Exception if an error occurs during node processing or indexing
     */
    private void processUpdatedNode(NodeContainer nodeContainer, long maxTxnCommitTime) throws Exception {
        if (nodeContainer == null || nodeContainer.getNodes().isEmpty()) {
            LOG.warn("No nodes found for processing.");
            return;
        }

        LOG.debug("Processing {} updated nodes", nodeContainer.getNodes().size());

        Set<Integer> uniqueAclIds = nodeContainer.getNodes().stream()
                .map(Node::getAclId)
                .collect(Collectors.toSet());
        LOG.debug("Fetching ACL readers for {} unique ACL IDs", uniqueAclIds.size());

        Map<Integer, List<String>> aclIdToReadersMap = alfrescoService.getAclReaders(uniqueAclIds);
        setReadersForNodes(nodeContainer, aclIdToReadersMap);

        // Build and execute bulk indexing request
        BulkRequest bulkRequest = openSearchRequestBuilder.buildBulkRequest(nodeContainer.getNodes(), maxTxnCommitTime);
        LOG.debug("Indexing {} nodes in bulk", nodeContainer.getNodes().size());
        indexer.index(bulkRequest);

        // Process the content for the updated nodes asynchronously
        nodeContentProcessor.processNodeContentAsync(nodeContainer.getNodes());
    }

    /**
     * Associates the readers for each node from the ACL-to-reader mapping and logs the assignment.
     *
     * @param nodeContainer       the container holding nodes to process
     * @param aclIdToReadersMap   the map of ACL ID to list of readers
     */
    private void setReadersForNodes(NodeContainer nodeContainer, Map<Integer, List<String>> aclIdToReadersMap) {
        for (Node node : nodeContainer.getNodes()) {
            List<String> readers = aclIdToReadersMap.getOrDefault(node.getAclId(), Collections.emptyList());
            node.setReaders(readers);
            LOG.debug("Set readers for node ID {}: {}", node.getId(), readers);
        }
    }

    /**
     * Processes a node marked as deleted by removing it from the index.
     *
     * @param transactionNode the node to delete
     * @throws Exception if an error occurs during deletion from the index
     */
    private void processDeletedNode(TransactionNode transactionNode) throws Exception {
        if (transactionNode == null || transactionNode.getNodeRef() == null) {
            LOG.warn("Transaction node or NodeRef is null, skipping deletion.");
            return;
        }

        String uuid = extractUuidFromNodeRef(transactionNode.getNodeRef());
        LOG.debug("Deleting document with NodeRef {}", transactionNode.getNodeRef());
        indexer.deleteDocument(uuid);
    }
}