package org.alfresco.repo.service.beans;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Diff {

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("oldChecksum")
    private Long oldChecksum;

    @JsonProperty("newChecksum")
    private Long newChecksum;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getOldChecksum() {
        return oldChecksum;
    }

    public void setOldChecksum(Long oldChecksum) {
        this.oldChecksum = oldChecksum;
    }

    public Long getNewChecksum() {
        return newChecksum;
    }

    public void setNewChecksum(Long newChecksum) {
        this.newChecksum = newChecksum;
    }

    @Override
    public String toString() {
        return "Diff{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", oldChecksum=" + oldChecksum +
                ", newChecksum=" + newChecksum +
                '}';
    }

}