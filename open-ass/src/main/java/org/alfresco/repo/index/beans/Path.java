package org.alfresco.repo.index.beans;

/**
 * Represents a path object within the node.
 */
public class Path {
    private String path; // Full path
    private String apath; // Another path field (could be an alias path or something else)
    private String qname;

    // Getters and setters

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getApath() {
        return apath;
    }

    public void setApath(String apath) {
        this.apath = apath;
    }

    public String getQname() {
        return qname;
    }

    public void setQname(String qname) {
        this.qname = qname;
    }

    @Override
    public String toString() {
        return "Path{" +
                "path='" + path + '\'' +
                ", apath='" + apath + '\'' +
                ", qname='" + qname + '\'' +
                '}';
    }
}