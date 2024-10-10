package org.alfresco.repo.service.beans;

import java.io.Serializable;
import java.util.List;

public class NamePath implements Serializable {
    private List<String> namePath;

    public List<String> getNamePath() {
        return namePath;
    }

    public void setNamePath(List<String> namePath) {
        this.namePath = namePath;
    }

    @Override
    public String toString() {
        return "NamePath{" +
                "namePath=" + namePath +
                '}';
    }
}
