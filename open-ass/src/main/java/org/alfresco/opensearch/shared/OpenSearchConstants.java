package org.alfresco.opensearch.shared;

public class OpenSearchConstants {

    // Output properties - Property names used for indexing
    public static final String ALIVE = "ALIVE";
    public static final String READER = "READER";
    public static final String DENIED = "DENIED";
    public static final String OWNER = "OWNER";
    public static final String METADATA_INDEXING_LAST_UPDATE = "METADATA_INDEXING_LAST_UPDATE";
    public static final String ASPECT = "ASPECT";
    public static final String PATH = "PATH";
    public static final String PATH_KEYWORD = "PATH.keyword";
    public static final String UNPREFIXED_PATH = "UNPREFIXED_PATH";
    public static final String PROPERTIES = "PROPERTIES";
    public static final String TAG = "TAG";
    public static final String TYPE = "TYPE";
    public static final String PRIMARY_HIERARCHY = "primaryHierarchy";
    public static final String PRIMARY_PARENT = "PRIMARYPARENT";
    public static final String PARENT = "PARENT";
    public static final String ANCESTOR = "ANCESTOR";
    public static final String STANDARD_ANCESTOR = "STANDARD_ANCESTOR";
    public static final String CATEGORY_ANCESTOR = "CATEGORY_ANCESTOR";
    public static final String NAME = "cm:name";
    public static final String USER_CREATOR = "cm:creator";
    public static final String USER_MODIFIER = "cm:modifier";
    public static final String CREATION_DATE_FIELD = "cm:created";
    public static final String MODIFICATION_DATE_FIELD = "cm:modified";
    public static final String ACCESS_DATE_FIELD = "cm:accessed";
    public static final String CONTENT_ID = "contentId";
    public static final String CONTENT_ATTRIBUTE_NAME = "cm:content";
    public static final String CONTENT_MIME_TYPE = "cm:content.mimetype";
    public static final String CONTENT_SIZE = "cm:content.size";
    public static final String CONTENT_ENCODING = "cm:content.encoding";
    public static final String CM_CATEGORY = "cm:category";
    public static final String CM_CATEGORIES = "cm:categories";
    public static final String CM_CONTENT_TR_EX = "cm:content.tr_ex";
    public static final String CM_CONTENT_TR_STATUS = "cm:content.tr_status";

    // Input properties - Property names used for taking information from the event
    public static final String OWNER_PROPERTY_NAME = "cm:owner";
    public static final String TYPE_STORE_ROOT = "sys:store_root";
    public static final String TYPE_DELETED = "sys:deleted";

    // Other
    public static final String CM_CATEGORY_ROOT = "/cm:categoryRoot/cm:generalclassifiable/";
}