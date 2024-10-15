package org.alfresco.repo.index.beans;

import org.alfresco.indexer.ModelMappingIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.alfresco.opensearch.index.OpenSearchRequestBuilder.extractUuid;

/**
 * Represents a node in the Alfresco repository.
 */
public class Node {

    private static final Logger LOG = LoggerFactory.getLogger(Node.class);

    private long id; // Unique identifier for the node
    private String tenantDomain; // Domain of the tenant to which the node belongs
    private String nodeRef; // Node reference identifier
    private String type; // Type of the node
    private int aclId; // Access Control List (ACL) identifier
    private int txnId; // Transaction identifier
    private Map<String, Serializable> properties; // Properties associated with the node
    private List<String> aspects; // Aspects associated with the node
    private List<Path> paths; // Paths to the node
    private List<NamePath> namePaths; // Paths to the node by name
    private List<String> ancestors;
    private List<String> parentAssocs;
    private Long parentAssocsCrc;
    private String owner;
    private List<String> readers;

    // Getters and setters

    /**
     * Retrieves the unique identifier of the node.
     *
     * @return The ID of the node.
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the node.
     *
     * @param id The ID of the node.
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Retrieves the domain of the tenant to which the node belongs.
     *
     * @return The tenant domain.
     */
    public String getTenantDomain() {
        return tenantDomain;
    }

    /**
     * Sets the domain of the tenant to which the node belongs.
     *
     * @param tenantDomain The tenant domain.
     */
    public void setTenantDomain(String tenantDomain) {
        this.tenantDomain = tenantDomain;
    }

    /**
     * Retrieves the node reference identifier.
     *
     * @return The node reference.
     */
    public String getNodeRef() {
        return nodeRef;
    }

    /**
     * Sets the node reference identifier.
     *
     * @param nodeRef The node reference.
     */
    public void setNodeRef(String nodeRef) {
        this.nodeRef = nodeRef;
    }

    /**
     * Retrieves the type of the node.
     *
     * @return The node type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the type of the node.
     *
     * @param type The node type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Retrieves the Access Control List (ACL) identifier of the node.
     *
     * @return The ACL identifier.
     */
    public int getAclId() {
        return aclId;
    }

    /**
     * Sets the Access Control List (ACL) identifier of the node.
     *
     * @param aclId The ACL identifier.
     */
    public void setAclId(int aclId) {
        this.aclId = aclId;
    }

    /**
     * Retrieves the transaction identifier of the node.
     *
     * @return The transaction identifier.
     */
    public int getTxnId() {
        return txnId;
    }

    /**
     * Sets the transaction identifier of the node.
     *
     * @param txnId The transaction identifier.
     */
    public void setTxnId(int txnId) {
        this.txnId = txnId;
    }

    /**
     * Retrieves the properties associated with the node.
     *
     * @return The node properties.
     */
    public Map<String, Serializable> getProperties() {
        return properties;
    }

    /**
     * Sets the properties associated with the node.
     *
     * @param properties The node properties.
     */
    public void setProperties(Map<String, Serializable> properties) {
        Map<String, Serializable> prefixedProperties = new HashMap<>();

        for (Map.Entry<String, Serializable> entry : properties.entrySet()) {
            String key = entry.getKey();
            int lastIndex = key.lastIndexOf("}");

            if (lastIndex < 0) {
                throw new IllegalArgumentException("Key must contain a closing brace '}'");
            }

            String prefix = ModelMappingIndexer.URL_TO_PREFIX.get(key.substring(0, lastIndex + 1));
            if (prefix == null) {
                LOG.error("Prefix missing for key '{}' in node '{}'. The Custom Content Model might not be deployed in the Repository. " +
                        "Please verify that the model is correctly registered and deployed.", key, nodeRef);
                prefix = key.substring(0, lastIndex + 1);
            }

            String prefixedKey = prefix + ":" + key.substring(lastIndex + 1);
            prefixedProperties.put(prefixedKey, entry.getValue());
        }

        this.properties = prefixedProperties;
    }

    public Map<String, Serializable> getPropertiesQName() {
        return properties;
    }

    /**
     * Retrieves the aspects associated with the node.
     *
     * @return The node aspects.
     */
    public List<String> getAspects() {
        return aspects;
    }

    /**
     * Sets the aspects associated with the node.
     *
     * @param aspects The node aspects.
     */
    public void setAspects(List<String> aspects) {
        this.aspects = aspects;
    }

    /**
     * Retrieves the paths to the node.
     *
     * @return The paths to the node.
     */
    public List<Path> getPaths() {
        return paths;
    }

    /**
     * Sets the paths to the node.
     *
     * @param paths The paths to the node.
     */
    public void setPaths(List<Path> paths) {
        this.paths = paths;
    }

    /**
     * Retrieves the paths to the node by name.
     *
     * @return The paths to the node by name.
     */
    public List<NamePath> getNamePaths() {
        return namePaths;
    }

    /**
     * Sets the paths to the node by name.
     *
     * @param namePaths The paths to the node by name.
     */
    public void setNamePaths(List<NamePath> namePaths) {
        this.namePaths = namePaths;
    }

    public List<String> getAncestors() { return ancestors; }
    public void setAncestors(List<String> ancestors) {
        this.ancestors = new ArrayList<>();
        ancestors.forEach(ancestor -> {
            this.ancestors.add(extractUuid(ancestor));
        });
    }

    public List<String> getParentAssocs() { return parentAssocs; }
    public void setParentAssocs(List<String> parentAssocs) {
        this.parentAssocs = parentAssocs;
    }

    public Long getParentAssocsCrc() {
        return parentAssocsCrc;
    }

    public void setParentAssocsCrc(Long parentAssocsCrc) {
        this.parentAssocsCrc = parentAssocsCrc;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<String> getReaders() {
        return readers;
    }

    public void setReaders(List<String> readers) {
        this.readers = readers;
    }

    @Override
    public String toString() {
        return "Node{" +
                "id=" + id +
                ", tenantDomain='" + tenantDomain + '\'' +
                ", nodeRef='" + nodeRef + '\'' +
                ", type='" + type + '\'' +
                ", aclId=" + aclId +
                ", txnId=" + txnId +
                ", properties=" + properties +
                ", aspects=" + aspects +
                ", paths=" + paths +
                ", namePaths=" + namePaths +
                ", ancestors=" + ancestors +
                ", parentAssocs=" + parentAssocs +
                ", parentAssocsCrc=" + parentAssocsCrc +
                ", owner='" + owner + '\'' +
                ", readers=" + readers +
                '}';
    }

}