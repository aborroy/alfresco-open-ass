package org.alfresco.indexer;

import jakarta.annotation.PostConstruct;
import org.alfresco.opensearch.ingest.Indexer;
import org.alfresco.repo.index.AlfrescoService;
import org.alfresco.repo.index.beans.Node;
import org.alfresco.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.alfresco.opensearch.shared.OpenSearchConstants.CONTENT_ATTRIBUTE_NAME;
import static org.alfresco.opensearch.shared.OpenSearchConstants.CONTENT_ID;
import static org.alfresco.utils.NodeUtils.extractUuidFromNodeRef;

/**
 * Service responsible for processing node content asynchronously for indexing.
 * Interacts with the Alfresco Solr API and ensures that only nodes with updated
 * content are reindexed in OpenSearch.
 */
@Component
public class NodeContentProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContentProcessor.class);
    public static final String SPACES_STORE = "SpacesStore";

    private ExecutorService executorService;

    @Value("${batch.indexer.content.threads}")
    private int contentThreads;

    @Autowired
    private AlfrescoService alfrescoService;

    @Autowired
    private Indexer indexer;

    /**
     * Initializes the thread pool for asynchronous node content processing.
     * This setup ensures that multiple nodes are processed concurrently.
     */
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(contentThreads);
        LOG.debug("Initialized NodeContentProcessor with {} threads", contentThreads);
    }

    /**
     * Asynchronously processes a list of nodes, submitting each node to the executor
     * service for parallel processing. Errors encountered during processing are
     * logged and do not halt execution for other nodes.
     *
     * @param nodes the list of nodes to process asynchronously
     */
    public void processNodeContentAsync(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            LOG.warn("No nodes provided for content processing.");
            return;
        }

        LOG.debug("Submitting {} nodes for asynchronous content processing.", nodes.size());

        CompletableFuture.runAsync(() ->
                nodes.forEach(node -> executorService.submit(() -> {
                    try {
                        LOG.debug("Processing content for node: {}", node.getNodeRef());
                        processNodeContent(node);
                    } catch (Exception e) {
                        LOG.error("Error processing content for node: {}", node.getNodeRef(), e);
                    }
                })), executorService);
    }

    /**
     * Processes the content of an individual node. The node is only indexed
     * if it belongs to the "SpacesStore" and its 'cm:content' property is valid.
     * If the content has changed since the last index operation, the content is
     * reindexed in OpenSearch.
     *
     * @param node the node to process and index
     * @throws Exception if an error occurs during node content retrieval or indexing
     */
    private void processNodeContent(Node node) throws Exception {
        if (node == null || node.getNodeRef() == null) {
            LOG.warn("Invalid node or node reference is null, skipping processing.");
            return;
        }

        String uuid = extractUuidFromNodeRef(node.getNodeRef());
        String storeIdentifier = (String) node.getProperties().get("sys:store-identifier");

        // Check if 'cm:content' property exists and is valid
        Map<?, ?> contentMap = (Map<?, ?>) node.getProperties().get(CONTENT_ATTRIBUTE_NAME);
        if (contentMap == null || !contentMap.containsKey(CONTENT_ID) || contentMap.get(CONTENT_ID) == null) {
            LOG.debug("Skipping node ID {}: 'cm:content' or 'CONTENT_ID' is missing or null", uuid);
            return;
        }

        String contentId = contentMap.get(CONTENT_ID).toString();

        // Only process nodes from the "SpacesStore"
        if (SPACES_STORE.equals(storeIdentifier)) {
            String contentIdInOS = indexer.getContentId(uuid);

            // Reindex if content ID has changed
            if (!contentId.equals(contentIdInOS)) {
                try {
                    LOG.debug("Fetching content for node ID {} with new content ID {}", uuid, contentId);
                    String content = alfrescoService.getNodeContent(node.getId());
                    indexer.indexContent(uuid, contentId, JsonUtils.escape(content));
                    LOG.info("Successfully indexed content for node ID {} with new content ID {}", uuid, contentId);
                } catch (Exception e) {
                    LOG.error("Failed to index content for node ID {}: {}", uuid, e.getMessage());
                    throw e;
                }
            } else {
                LOG.debug("Skipping reindexing for node ID {}: Content ID is unchanged: {}", uuid, contentId);
            }
        } else {
            LOG.debug("Skipping node ID {}: Node is not stored in SpacesStore", uuid);
        }
    }
}