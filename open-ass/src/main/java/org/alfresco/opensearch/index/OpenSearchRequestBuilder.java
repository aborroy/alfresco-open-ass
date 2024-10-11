package org.alfresco.opensearch.index;

import org.alfresco.opensearch.shared.AlfrescoQualifiedNameTranslator;
import org.alfresco.repo.service.beans.NamePath;
import org.alfresco.repo.service.beans.Node;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static org.alfresco.opensearch.shared.OpenSearchConstants.*;

@Component
public class OpenSearchRequestBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchRequestBuilder.class);

    @Value("${opensearch.index.name}")
    private String indexName;

    public BulkRequest buildBulkRequest(List<Node> nodes, long commitTimeMs) {
        BulkRequest bulkRequest = new BulkRequest();

        nodes.stream()
                .map(node -> createRequest(node, commitTimeMs))
                .flatMap(Collection::stream)
                .forEach(bulkRequest::add);

        return bulkRequest;
    }

    private List<DocWriteRequest<?>> createRequest(Node node, long commitTimeMs) {
        DocWriteRequest<?> upsertRequest = createUpsertRequest(node, commitTimeMs);
        return Collections.singletonList(upsertRequest);
    }

    private DocWriteRequest<?> createUpsertRequest(Node node, long commitTimeMs) {
        UpdateRequest updateRequest = new UpdateRequest();

        Map<String, Object> fields = extractFields(node, commitTimeMs);
        Set<String> fieldNames = fields.keySet();

        updateRequest.script(new Script(ScriptType.INLINE, "painless", buildSourceField(fieldNames), fields));
        updateRequest.upsert(fields);

        updateRequest.id(extractUuid(node.getNodeRef()));
        updateRequest.retryOnConflict(5);
        updateRequest.index(indexName);
        return updateRequest;
    }

    public static String extractUuid(String input) {
        String regex = ".+://.+/(.+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    public String buildSourceField(Set<String> fieldNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("if (ctx._source." + METADATA_INDEXING_LAST_UPDATE +
                " > params." + METADATA_INDEXING_LAST_UPDATE +
                ") { ctx.op = 'noop'} else { ");
        for (String fieldName : fieldNames) {
            sb.append("ctx._source['");
            sb.append(fieldName);
            sb.append("'] = params['");
            sb.append(fieldName);
            sb.append("']; ");
        }
        sb.append("}");
        return sb.toString();
    }

    public Map<String, Object> extractFields(Node node, long commitTimeMs) {

        Map<String, Object> fields = new HashMap<>();

        addType(node.getType(), fields);
        addPrimaryHierarchy(node.getAncestors(), fields);
        addParents(node.getAncestors(), fields);
        addAclInformation(node, fields);
        addNodeTimeInformation(node, fields, commitTimeMs);
        addUserInformation(node, fields);
        addDateInformation(node, fields);
        addName(node, fields);
        addProperties(node, fields);
        addAspects(node, fields);
        addTags(node.getNamePaths(), fields);
        addContentInfo(node, fields);
        setDocumentAlive(fields, true);

        LOG.debug("Properties to be indexed for node {}: {}", node.getId(), fields);

        return fields;
    }

    private void addType(String type, Map<String, Object> fields) {
        addEncodedField(fields, TYPE, type);
    }

    public static void addPrimaryHierarchy(List<String> ancestors, Map<String, Object> fields) {
        ofNullable(ancestors)
                .filter(not(Collection::isEmpty))
                .flatMap(hierarchy ->
                        hierarchy.stream().findFirst())
                .ifPresent(primaryParent -> fields.put(PRIMARY_PARENT, primaryParent));
    }

    public static void addParents(Collection<String> allParents, Map<String, Object> outputFields) {
        ofNullable(allParents)
                .filter(not(Collection::isEmpty))
                .ifPresent(parents -> outputFields.put(PARENT, parents));
    }

    private void addAclInformation(Node node, Map<String, Object> fields) {
        addEncodedField(fields, READER, node.getReaders());
    }

    private void addNodeTimeInformation(Node node, Map<String, Object> fields, long commitTimeMs) {
        addEncodedField(fields, METADATA_INDEXING_LAST_UPDATE, commitTimeMs);
    }

    private void addUserInformation(Node node, Map<String, Object> fields) {
        ofNullable(node.getProperties().get(USER_CREATOR))
                .ifPresent(creator -> addEncodedField(fields, USER_CREATOR, node.getProperties().get(USER_CREATOR)));
        ofNullable(node.getProperties().get(USER_MODIFIER))
                .ifPresent(modifier -> addEncodedField(fields, USER_MODIFIER, node.getProperties().get(USER_MODIFIER)));
    }

    private void addDateInformation(Node node, Map<String, Object> fields) {
        ofNullable(node.getProperties().get(CREATION_DATE_FIELD))
                .ifPresent(creationDate -> addEncodedField(fields, CREATION_DATE_FIELD, node.getProperties().get(CREATION_DATE_FIELD)));
        ofNullable(node.getProperties().get(MODIFICATION_DATE_FIELD))
                .ifPresent(modificationDate -> addEncodedField(fields, MODIFICATION_DATE_FIELD, node.getProperties().get(MODIFICATION_DATE_FIELD)));
    }

    private void addName(Node node, Map<String, Object> fields) {
        addEncodedField(fields, NAME, node.getProperties().get(NAME));
    }

    private void addProperties(Node node, Map<String, Object> fields) {
        if (node.getProperties() != null) {
            Map<String, Serializable> properties = new HashMap<>(node.getProperties());
            properties.keySet().remove(CM_CONTENT_TR_STATUS);
            properties.keySet().remove(CONTENT_ATTRIBUTE_NAME);
            properties.forEach((key, value) -> addEncodedField(fields, key, value));
            getOwner(properties, node).ifPresent(owner -> addEncodedField(fields, OWNER, owner));
            addEncodedField(fields, PROPERTIES, properties.keySet());
        }
    }

    private Optional<Serializable> getOwner(Map<String, Serializable> properties, Node node) {
        return Optional.ofNullable(properties.get(OWNER_PROPERTY_NAME))
                .or(() -> Optional.ofNullable(node.getProperties().get(USER_MODIFIER)));
    }

    private void addAspects(Node node, Map<String, Object> fields) {
        addEncodedField(fields, ASPECT, node.getAspects());
    }

    private void addTags(List<NamePath> namePaths, Map<String, Object> fields) {
        List<String> tags = namePaths.stream()
                .filter(namePath -> namePath.getNamePath().size() > 1 && "Tags".equals(namePath.getNamePath().get(0)))
                .map(namePath -> namePath.getNamePath().get(1))
                .collect(Collectors.toList());
        addEncodedField(fields, TAG, tags);
    }

    protected void addContentInfo(Node node, Map<String, Object> fields) {
        Object contentObj = node.getProperties().get(CONTENT_ATTRIBUTE_NAME);
        if(contentObj != null) {
            Map<String, String> content = (Map<String, String>) contentObj;
            addEncodedField(fields, CONTENT_MIME_TYPE, content.get("mimetype"));
            addEncodedField(fields, CONTENT_SIZE, content.get("size"));
            addEncodedField(fields, CONTENT_ENCODING, content.get("encoding"));
        }
    }

    private void setDocumentAlive(Map<String, Object> properties, boolean alive){
        addEncodedField(properties, ALIVE, alive);
    }

    protected void addEncodedField(Map<String, Object> fields, String key, Object value) {
       if (value instanceof Collection) {
           value = processCollection(value);
       } else if (value instanceof Map) {
           value = processMap(value);
       } else {
           value = prepareField(value);
       }
       fields.put(AlfrescoQualifiedNameTranslator.encode(key), value);
    }

    /**
     * Cases to cover:
     * - List of Map elements
     * - Each map can contain "locale" and "value" keys or only "locale"
     * @param obj The object to check.
     * @return true if the object is a list of maps containing the specified keys; false otherwise.
     */
    private boolean isLocaleField(Object obj) {
        if (obj instanceof List<?> list && !list.isEmpty()) {
            Object element = list.get(0);
            if (element instanceof Map<?, ?> map) {
                return (map.containsKey("locale") &&
                        (map.size() == 2 && map.containsKey("value") || map.size() == 1));
            }
        }
        return false;
    }

    public static String getValueFromLocaleObject(Object obj) {
        ArrayList<?> list = (ArrayList<?>) obj;
        Map<?, ?> map = (Map<?, ?>) list.get(0);
        Object value = map.get("value");
        if (value != null) {
            return value.toString();
        }
        return "";
    }

    private Object processMap(Object map) {
        return ((Map<?, ?>) map).entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private Object processCollection(Object collection) {
        if (isLocaleField(collection)) {
            return getValueFromLocaleObject(collection);
        } else {
            return ((Collection) collection).stream().map(this::prepareField).collect(collection instanceof Set ? toSet() : toList());
        }
    }

    private Object prepareField(Object field) {
        if (field instanceof Map && ((Map<?, ?>) field).containsKey("id")) {
            return ((Map<?, ?>) field).get("id");
        }
        return field;
    }

}
