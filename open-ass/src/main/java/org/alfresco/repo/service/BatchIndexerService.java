package org.alfresco.repo.service;

import jakarta.annotation.PostConstruct;
import org.alfresco.opensearch.index.IndexService;
import org.alfresco.repo.service.beans.Transaction;
import org.alfresco.repo.service.beans.TransactionContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for batch indexing documents into OpenSearch.
 *
 * Retrieves transactions from Alfresco, indexes them into OpenSearch, and updates
 * index control status. It also ensures model mappings are synchronized before processing.
 */
@Service
public class BatchIndexerService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchIndexerService.class);

    @Value("${batch.indexer.transaction.maxResults}")
    private int maxResults;

    @Autowired
    private IndexService indexService;

    @Autowired
    private ModelMappingService modelMappingService;

    @Autowired
    private NodeProcessorService nodeProcessorService;

    /**
     * Initializes the service by creating Alfresco-specific indexes in OpenSearch.
     * This ensures that OpenSearch has the necessary indexes before any transactions are indexed.
     *
     * @throws Exception if the index creation fails.
     */
    @PostConstruct
    public void initialize() throws Exception {
        indexService.createAlfrescoIndexes();
    }

    /**
     * Scheduled method that indexes unindexed transactions from Alfresco into OpenSearch.
     * It syncs model mappings, retrieves unindexed transactions, processes them, and updates
     * the control index status in OpenSearch after successful indexing.
     *
     * @throws Exception if an error occurs during the indexing process.
     */
    @Scheduled(cron = "${batch.indexer.cron}")
    public void index() throws Exception {
        // Synchronize model mappings before processing transactions.
        LOG.debug("Syncing model mappings before starting the indexing process.");
        modelMappingService.syncMappingFromModels();

        // Get the last successfully indexed transaction ID, and fetch new transactions from Alfresco.
        long lastTransactionId = indexService.getAlfrescoControlIndexStatus() + 1;
        LOG.debug("Last indexed transaction ID: {}. Fetching new transactions.", lastTransactionId);

        TransactionContainer retrievedTransactions = nodeProcessorService.retrieveTransactions(lastTransactionId, maxResults);
        LOG.debug("Retrieved {} transactions from Alfresco.", retrievedTransactions.getTransactions().size());

        // Set the initial min/max transaction IDs for the current batch.
        long minTxnId = lastTransactionId;
        long maxTxnId = lastTransactionId;

        // Retrieve the highest transaction ID currently available in Alfresco.
        long maxTxnIdRepository = retrievedTransactions.getMaxTxnId();
        LOG.debug("Maximum transaction ID in Alfresco: {}", maxTxnIdRepository);

        // Process and index transactions if any are retrieved.
        if (!retrievedTransactions.getTransactions().isEmpty()) {
            // Determine the minimum and maximum transaction IDs in the batch.
            Optional<Long> optionalMinTxnId = retrievedTransactions.getTransactions().stream()
                    .map(Transaction::getId).min(Long::compare);
            Optional<Long> optionalMaxTxnId = retrievedTransactions.getTransactions().stream()
                    .map(Transaction::getId).max(Long::compare);

            minTxnId = optionalMinTxnId.orElse(minTxnId);
            maxTxnId = optionalMaxTxnId.orElse(maxTxnId);

            LOG.info("Indexing content for transactions between {} and {}", minTxnId, maxTxnId);

            // Process transactions and index them into OpenSearch.
            LOG.debug("Processing transactions with minTxnId: {} and maxTxnId: {}", minTxnId, maxTxnId);
            nodeProcessorService.processTransactions(minTxnId, maxTxnId, retrievedTransactions.getMaxTxnCommitTime());

            // Update the control index with the highest indexed transaction ID.
            LOG.debug("Updating control index status with maxTxnId: {}", maxTxnId);
            indexService.updateAlfrescoControlIndexStatus(maxTxnId);
        } else {
            // Log if no new transactions were found for indexing.
            LOG.info(
                    """
                    All transactions have been indexed:
                    - Maximum Transaction ID in Alfresco: {}
                    - Maximum Transaction ID in OpenSearch: {}
                    """, maxTxnIdRepository, indexService.getAlfrescoControlIndexStatus());
        }
    }
}