package org.alfresco.repo.service.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ModelDiffs {

    @JsonProperty("diffs")
    private List<Diff> diffs;

    public List<Diff> getDiffs() {
        return diffs;
    }

    public void setDiffs(List<Diff> diffs) {
        this.diffs = diffs;
    }

    @Override
    public String toString() {
        return "ModelDiffs{" +
                "diffs=" + diffs +
                '}';
    }
}
