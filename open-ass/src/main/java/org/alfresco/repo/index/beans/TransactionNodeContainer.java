package org.alfresco.repo.index.beans;

import java.util.List;

/**
 * Represents a container for a list of transaction nodes in the Alfresco repository.
 */
public class TransactionNodeContainer {
    private List<TransactionNode> nodes; // List of transaction nodes contained in the container

    /**
     * Retrieves the list of transaction nodes contained in the container.
     *
     * @return The list of transaction nodes.
     */
    public List<TransactionNode> getNodes() {
        return nodes;
    }

    /**
     * Sets the list of transaction nodes for the container.
     *
     * @param nodes The list of transaction nodes to set.
     */
    public void setNodes(List<TransactionNode> nodes) {
        this.nodes = nodes;
    }


    @Override
    public String toString() {
        return "TransactionNodeContainer{" +
                "nodes=" + nodes +
                '}';
    }

}