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

@Component
public class AlfrescoService {

    private static final Logger LOG = LoggerFactory.getLogger(AlfrescoService.class);

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

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

    public TransactionNodeContainer getTransactionNodeContainer(long minTxnId, long maxTxnId) throws Exception {
        String payload = String.format("{\"fromTxnId\": %d, \"toTxnId\": %d}", minTxnId, maxTxnId);
        LOG.debug("Requesting transaction nodes between IDs {} and {}", minTxnId, maxTxnId);
        String nodesResponse = alfrescoSolrApiClient.executePostRequest("nodes", payload);
        return objectMapper.readValue(nodesResponse, TransactionNodeContainer.class);
    }

    public NodeContainer getMetadata(Long nodeId) throws Exception {
        String payload = createMetadataRequestPayload(nodeId);
        LOG.debug("Requesting metadata for node ID {}", nodeId);
        String metadataResponse = alfrescoSolrApiClient.executePostRequest("metadata", payload);
        return objectMapper.readValue(metadataResponse, NodeContainer.class);
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
     * Fetches ACL readers for the given ACL IDs.
     *
     * @param uniqueAclIds the set of unique ACL IDs
     * @return the response containing ACL readers as a JSON string
     * @throws Exception if an error occurs during the request
     */
    public Map<Integer, List<String>> fetchAclReaders(Set<Integer> uniqueAclIds) throws Exception {
        LOG.debug("Fetching ACL readers for ACL IDs: {}", uniqueAclIds);
        String aclPayload = objectMapper.writeValueAsString(Map.of("aclIds", uniqueAclIds));
        return mapAclReaders(alfrescoSolrApiClient.executePostRequest("aclsReaders", aclPayload));
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

    public ModelDiffs getModelDiffs() throws Exception {
        String payload = "{\"models\": [] }";
        String modelsDiffResponse = alfrescoSolrApiClient.executePostRequest("modelsdiff", payload);
        LOG.debug("Received models difference response: {}", modelsDiffResponse);
        return objectMapper.readValue(modelsDiffResponse, ModelDiffs.class);
    }

    /**
     * Fetches the XML content of a model from the Alfresco Solr API.
     *
     * @param modelQName the QName of the model.
     * @return the XML content of the model as a String.
     * @throws Exception if an error occurs while fetching the model XML.
     */
    public String fetchModelXmlContent(String modelQName) throws Exception {
        String endpoint = String.format("model?modelQName=%s", URLEncoder.encode(modelQName, StandardCharsets.UTF_8));
        LOG.debug("Sending GET request to endpoint: {}", endpoint);
        return alfrescoSolrApiClient.executeGetRequest(endpoint);
    }

    public String getNodeContent(Long nodeId) throws Exception {
        return alfrescoSolrApiClient.executeGetRequest("textContent?nodeId=" + nodeId);
    }

}
