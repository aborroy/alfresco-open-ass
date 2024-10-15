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
 * This class processes node content asynchronously for indexing, interacting with the Alfresco Solr API 
 * and ensuring only nodes with updated content are reindexed.
 */
@Component
public class NodeContentProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(NodeContentProcessor.class);
    public static final String SPACES_STORE = "SpacesStore";

    private ExecutorService executorService;

    @Value("${batch.indexer.content.threads}")
    private int contentThreads;

    @Autowired
    AlfrescoService alfrescoService;

    @Autowired
    private Indexer indexer;

    /**
     * Initializes the thread pool for processing nodes concurrently.
     */
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(contentThreads);
        LOG.debug("Initialized NodeContentProcessor with {} threads", contentThreads);
    }

    /**
     * Processes a list of nodes asynchronously, submitting tasks to the executor service for each node.
     * Any exceptions that occur during node content processing are logged but do not stop the execution of
     * other nodes.
     *
     * @param nodes the list of nodes to process
     */
    public void processNodeContentAsync(List<Node> nodes) {
        CompletableFuture.runAsync(() ->
                nodes.forEach(node -> executorService.submit(() -> {
                    try {
                        LOG.debug("Processing node content: {}", node);
                        processNodeContent(node);
                    } catch (Exception e) {
                        LOG.error("Error processing content for node: {}", node, e);
                    }
                })), executorService);
    }

    /**
     * Processes an individual node by retrieving its content and indexing it if applicable.
     * Only nodes from the "SpacesStore" with a valid 'cm:content' property are considered for indexing.
     *
     * @param node the node to process
     * @throws Exception if an error occurs during node processing
     */
    private void processNodeContent(Node node) throws Exception {
        String uuid = extractUuidFromNodeRef(node.getNodeRef());
        String storeIdentifier = (String) node.getProperties().get("sys:store-identifier");

        // Check if 'cm:content' exists and is not null
        Map<?, ?> contentMap = (Map<?, ?>) node.getProperties().get(CONTENT_ATTRIBUTE_NAME);
        if (contentMap == null || !contentMap.containsKey(CONTENT_ID) || contentMap.get(CONTENT_ID) == null) {
            LOG.debug("Skipping node ID {}: 'cm:content' or 'CONTENT_ID' is missing or null", uuid);
            return;
        }

        String contentId = contentMap.get(CONTENT_ID).toString();

        // Skip nodes in non-indexable stores
        if (SPACES_STORE.equals(storeIdentifier)) {
            String contentIdInOS = indexer.getContentId(uuid);
            // Reindex only if the content ID has changed
            if (!contentId.equals(contentIdInOS)) {
                String content = alfrescoService.getNodeContent(node.getId());
                indexer.indexContent(uuid, contentId, JsonUtils.escape(content));
                LOG.debug("Indexed content for node ID {} with new content ID {}", uuid, contentId);
            } else {
                LOG.debug("Skipping indexing for node ID {}: Content ID has not changed: {}", uuid, contentId);
            }
        } else {
            LOG.debug("Skipping indexing for node ID {}: not in SpacesStore", uuid);
        }
    }
}