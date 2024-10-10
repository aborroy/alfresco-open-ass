package org.alfresco.repo.service.beans;

public class Model {

    private String name;
    private String qname;
    private String prefix;

    public String getPrefix() {
        return prefix;
    }

    public Model withPrefix(String prefix) {
        this.prefix = prefix;
        return this;
    }

    public String getQname() {
        return qname;
    }

    public Model withQname(String qname) {
        this.qname = qname;
        return this;
    }

    public String getName() {
        return name;
    }

    public Model withName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        return "Model{" +
                "name='" + name + '\'' +
                ", qname='" + qname + '\'' +
                ", prefix='" + prefix + '\'' +
                '}';
    }

}
