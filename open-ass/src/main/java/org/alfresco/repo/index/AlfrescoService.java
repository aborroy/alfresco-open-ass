package org.alfresco.repo.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.repo.client.AlfrescoSolrApiClientFactory;
import org.alfresco.repo.index.beans.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service class to interact with Alfresco's Solr API for handling transactions, nodes, ACL readers, and models.
 * It provides methods to retrieve transaction data, metadata for nodes, ACL reader information, and model-related details.
 */
@Component
public class AlfrescoService {

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoService.class);

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Retrieves transactions from the Alfresco Solr API based on the provided last transaction ID and result limit.
     *
     * @param lastTransactionId the ID of the last indexed transaction
     * @param maxResults        the maximum number of transactions to retrieve
     * @return a {@link TransactionContainer} containing the retrieved transactions
     * @throws Exception if an error occurs during the API request
     */
    public TransactionContainer retrieveTransactions(long lastTransactionId, int maxResults) throws Exception {
        String endpoint = String.format("transactions?minTxnId=%d&maxResults=%d", lastTransactionId, maxResults);
        LOG.debug("Sending request to retrieve transactions starting from ID {} with maxResults {}", lastTransactionId, maxResults);

        String jsonResponse = alfrescoSolrApiClient.executeGetRequest(endpoint);
        TransactionContainer transactions = objectMapper.readValue(jsonResponse, TransactionContainer.class);

        LOG.debug("Successfully retrieved {} transactions starting from ID {}", transactions.getTransactions().size(), lastTransactionId);
        return transactions;
    }

    /**
     * Retrieves a container of transaction nodes from the Alfresco Solr API within the specified transaction ID range.
     *
     * @param minTxnId the minimum transaction ID
     * @param maxTxnId the maximum transaction ID
     * @return a {@link TransactionNodeContainer} containing nodes within the specified range
     * @throws Exception if an error occurs during the API request
     */
    public TransactionNodeContainer getTransactionNodeContainer(long minTxnId, long maxTxnId) throws Exception {
        String payload = String.format("{\"fromTxnId\": %d, \"toTxnId\": %d}", minTxnId, maxTxnId);
        LOG.debug("Requesting transaction nodes between IDs {} and {}", minTxnId, maxTxnId);

        String nodesResponse = alfrescoSolrApiClient.executePostRequest("nodes", payload);
        TransactionNodeContainer container = objectMapper.readValue(nodesResponse, TransactionNodeContainer.class);

        LOG.debug("Received {} nodes between transaction IDs {} and {}", container.getNodes().size(), minTxnId, maxTxnId);
        return container;
    }

    /**
     * Retrieves metadata for a specific node from the Alfresco Solr API.
     *
     * @param nodeId the ID of the node
     * @return a {@link NodeContainer} containing metadata for the requested node
     * @throws Exception if an error occurs during the API request
     */
    public NodeContainer getMetadata(Long nodeId) throws Exception {
        String payload = createMetadataRequestPayload(nodeId);
        LOG.debug("Requesting metadata for node ID {}", nodeId);

        String metadataResponse = alfrescoSolrApiClient.executePostRequest("metadata", payload);
        NodeContainer container = objectMapper.readValue(metadataResponse, NodeContainer.class);

        LOG.debug("Successfully retrieved metadata for node ID {}", nodeId);
        return container;
    }

    /**
     * Creates the payload for a metadata request for the specified node ID.
     *
     * @param nodeId the ID of the node
     * @return the payload as a JSON string
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
     * Retrieves ACL readers for the provided ACL IDs from the Alfresco Solr API.
     *
     * @param uniqueAclIds a set of unique ACL IDs
     * @return a map of ACL IDs to their respective list of readers
     * @throws Exception if an error occurs during the API request
     */
    public Map<Integer, List<String>> getAclReaders(Set<Integer> uniqueAclIds) throws Exception {
        LOG.debug("Fetching ACL readers for ACL IDs: {}", uniqueAclIds);

        String aclPayload = objectMapper.writeValueAsString(Map.of("aclIds", uniqueAclIds));
        String aclResponse = alfrescoSolrApiClient.executePostRequest("aclsReaders", aclPayload);

        Map<Integer, List<String>> aclReadersMap = mapAclReaders(aclResponse);
        LOG.debug("Successfully fetched ACL readers for {} ACL IDs", aclReadersMap.size());

        return aclReadersMap;
    }

    /**
     * Maps ACL reader data from the API response to a map of ACL IDs and their associated readers.
     *
     * @param aclResponse the JSON response containing ACL reader data
     * @return a map of ACL IDs to lists of readers
     * @throws Exception if an error occurs during parsing
     */
    private Map<Integer, List<String>> mapAclReaders(String aclResponse) throws Exception {
        AclReadersResponse aclReadersResponse = objectMapper.readValue(aclResponse, AclReadersResponse.class);
        LOG.debug("Mapping ACL readers for {} ACL IDs", aclReadersResponse.getAclsReaders().size());

        return aclReadersResponse.getAclsReaders().stream()
                .collect(Collectors.toMap(AclReader::getAclId, AclReader::getReaders));
    }

    /**
     * Retrieves differences in model definitions from the Alfresco Solr API.
     *
     * @return a {@link ModelDiffs} object representing the differences between models
     * @throws Exception if an error occurs during the API request
     */
    public ModelDiffs getModelDiffs() throws Exception {
        String payload = "{\"models\": []}";
        LOG.debug("Requesting model differences from Solr API");

        String modelsDiffResponse = alfrescoSolrApiClient.executePostRequest("modelsdiff", payload);
        ModelDiffs modelDiffs = objectMapper.readValue(modelsDiffResponse, ModelDiffs.class);

        LOG.debug("Received model differences");
        return modelDiffs;
    }

    /**
     * Retrieves the XML content for a specific model based on its QName.
     *
     * @param modelQName the QName of the model
     * @return the XML content of the model
     * @throws Exception if an error occurs during the request
     */
    public String getModelXmlContent(String modelQName) throws Exception {
        String endpoint = String.format("model?modelQName=%s", URLEncoder.encode(modelQName, StandardCharsets.UTF_8));
        LOG.debug("Requesting model XML content for QName: {}", modelQName);

        return alfrescoSolrApiClient.executeGetRequest(endpoint);
    }

    /**
     * Retrieves the content of a node based on its node ID from the Alfresco Solr API.
     *
     * @param nodeId the ID of the node
     * @return the text content of the node
     * @throws Exception if an error occurs during the request
     */
    public String getNodeContent(Long nodeId) throws Exception {
        LOG.debug("Requesting content for node ID {}", nodeId);
        return alfrescoSolrApiClient.executeGetRequest("textContent?nodeId=" + nodeId);
    }
}