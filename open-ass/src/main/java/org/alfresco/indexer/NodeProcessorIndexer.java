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
 * Service for processing nodes and transactions using the Alfresco Solr API and OpenSearch.
 * This class handles the retrieval of transactions, processing of node metadata,
 * indexing of nodes, and deletion of nodes from the index.
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
     * Processes transactions between the specified minimum and maximum transaction IDs.
     *
     * @param minTxnId           the minimum transaction ID to process
     * @param maxTxnId           the maximum transaction ID to process
     * @param maxTxnCommitTime   the maximum commit time for the transaction list
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
     * Processes an individual raw node, handling metadata retrieval and indexing.
     *
     * @param transactionNode     the raw node to process
     * @param maxTxnCommitTime    the maximum commit time for the transaction list
     * @throws Exception if an error occurs during processing
     */
    private void processRawNode(TransactionNode transactionNode, long maxTxnCommitTime) throws Exception {
        NodeContainer nodeContainer = alfrescoService.getMetadata(transactionNode.getId());
        LOG.debug("Received metadata for node ID {}", transactionNode.getId());

        switch (transactionNode.getStatus()) {
            case "u":
                LOG.debug("Processing updated node with ID {}", transactionNode.getId());
                processUpdatedNode(nodeContainer, maxTxnCommitTime);
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
     * Processes an updated node by retrieving its metadata and indexing it.
     *
     * @param nodeContainer     the response containing the node metadata
     * @param maxTxnCommitTime    the maximum commit time for the transaction list
     * @throws Exception if an error occurs during processing
     */
    private void processUpdatedNode(NodeContainer nodeContainer, long maxTxnCommitTime) throws Exception {
        LOG.debug("Processing updated node. Metadata received for {} nodes", nodeContainer.getNodes().size());

        Set<Integer> uniqueAclIds = nodeContainer.getNodes().stream()
                .map(Node::getAclId)
                .collect(Collectors.toSet());
        LOG.debug("Fetching ACL readers for {} unique ACL IDs", uniqueAclIds.size());

        Map<Integer, List<String>> aclIdToReadersMap = alfrescoService.fetchAclReaders(uniqueAclIds);

        setReadersForNodes(nodeContainer, aclIdToReadersMap);
        BulkRequest bulkRequest = openSearchRequestBuilder.buildBulkRequest(nodeContainer.getNodes(), maxTxnCommitTime);
        LOG.debug("Indexing {} nodes in bulk", nodeContainer.getNodes().size());
        indexer.index(bulkRequest);

        // Update content for indexed nodes asynchronously
        nodeContentProcessor.processNodeContentAsync(nodeContainer.getNodes());
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