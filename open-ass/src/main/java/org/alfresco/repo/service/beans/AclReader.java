package org.alfresco.repo.service.beans;

import java.util.List;

public class AclReader {

    private int aclId;
    private int aclChangeSetId;
    private String tenantDomain;
    private List<String> readers;
    private List<String> denied;

    public int getAclId() {
        return aclId;
    }

    public void setAclId(int aclId) {
        this.aclId = aclId;
    }

    public int getAclChangeSetId() {
        return aclChangeSetId;
    }

    public void setAclChangeSetId(int aclChangeSetId) {
        this.aclChangeSetId = aclChangeSetId;
    }

    public String getTenantDomain() {
        return tenantDomain;
    }

    public void setTenantDomain(String tenantDomain) {
        this.tenantDomain = tenantDomain;
    }

    public List<String> getReaders() {
        return readers;
    }

    public void setReaders(List<String> readers) {
        this.readers = readers;
    }

    public List<String> getDenied() {
        return denied;
    }

    public void setDenied(List<String> denied) {
        this.denied = denied;
    }

    @Override
    public String toString() {
        return "AclReader{" +
                "aclId=" + aclId +
                ", aclChangeSetId=" + aclChangeSetId +
                ", tenantDomain='" + tenantDomain + '\'' +
                ", readers=" + readers +
                ", denied=" + denied +
                '}';
    }
}
