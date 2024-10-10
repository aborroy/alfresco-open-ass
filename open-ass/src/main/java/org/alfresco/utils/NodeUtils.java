package org.alfresco.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeUtils {

    private static final Logger LOG = LoggerFactory.getLogger(NodeUtils.class);

    /**
     * Extracts the UUID from a given node reference.
     *
     * @param nodeRef the node reference
     * @return the UUID of the node
     * @throws IllegalArgumentException if the node reference is invalid
     */
    public static String extractUuidFromNodeRef(String nodeRef) {
        int index = nodeRef.lastIndexOf("/");
        if (index == -1) {
            LOG.error("Invalid node reference: {}", nodeRef);
            throw new IllegalArgumentException("Invalid node reference: " + nodeRef);
        }
        return nodeRef.substring(index + 1);
    }

}
