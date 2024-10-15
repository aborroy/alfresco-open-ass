package org.alfresco.repo.index.beans;

import java.util.List;

public class AclReadersResponse {

    private List<AclReader> aclsReaders;

    public List<AclReader> getAclsReaders() {
        return aclsReaders;
    }

    public void setAclsReaders(List<AclReader> aclsReaders) {
        this.aclsReaders = aclsReaders;
    }

    @Override
    public String toString() {
        return "AclReadersResponse{" +
                "aclsReaders=" + aclsReaders +
                '}';
    }
}
