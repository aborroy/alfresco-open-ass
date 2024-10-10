package org.alfresco.opensearch.shared;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class AlfrescoQualifiedNameTranslator {

    /**
     * From the URLEncoder official JavaDoc: "Data characters that are allowed in a URI but do not
     * have a reserved purpose are called unreserved. These include upper and lower case letters,
     * decimal digits, and a limited set of punctuation marks and symbols.
     *
     * <p>unreserved = alphanum | mark
     *
     * <p>mark = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
     *
     * <p>Unreserved characters can be escaped without changing the semantics of the URI, but this
     * should not be done unless the URI is being used in a context that does not allow the unescaped
     * character to appear."
     *
     * <p>In our context there are some special characters that are reserved in Elasticsearch and not
     * encoded out of the box from the URLEncoder: mark = "-" | "+" | "." | "*" | " "
     *
     * <p>They are replaced explicitly to be consistent with the rest of the encoding. N.B. it's not
     * mandatory to encode "-" | "+" | "*" , but they can cause troubles at query time
     *
     * @param qualifiedName
     * @return
     */
    public static String encode(String qualifiedName) {
        return URLEncoder
                .encode(qualifiedName, StandardCharsets.UTF_8)
                .replaceAll("\\.", "%2E")
                .replaceAll("\\-", "%2D")
                .replaceAll("\\*", "%2A")
                .replaceAll("\\+", "%20");
    }

    public static String decode(String elasticsearchFieldName) {
        return URLDecoder.decode(elasticsearchFieldName, StandardCharsets.UTF_8);
    }
}
