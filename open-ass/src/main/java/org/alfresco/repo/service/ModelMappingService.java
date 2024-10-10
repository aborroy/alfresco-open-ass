package org.alfresco.repo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.alfresco.opensearch.client.AlfrescoSolrApiClientFactory;
import org.alfresco.repo.service.beans.Diff;
import org.alfresco.repo.service.beans.Model;
import org.alfresco.repo.service.beans.ModelDiffs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Service class responsible for managing model-to-prefix mappings.
 * Synchronizes model definitions from Alfresco Solr API and populates
 * a map where each model's QName is mapped to its associated prefix.
 */
@Service
public class ModelMappingService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMappingService.class);

    @Autowired
    private AlfrescoSolrApiClientFactory alfrescoSolrApiClient;

    // Mapping Model QName to prefix, e.g., "http://www.alfresco.org/model/content/1.0" to "cm"
    public static final Map<String, String> URL_TO_PREFIX = new TreeMap<>();

    /**
     * Synchronizes models by fetching their details from the Alfresco Solr API and populating
     * the map with the QName and prefix of each model.
     *
     * @throws Exception if any error occurs during the synchronization process.
     */
    public void syncMappingFromModels() throws Exception {
        LOG.debug("Starting synchronization of model mappings.");

        String payload = "{\"models\": [] }";
        String modelsDiffResponse = alfrescoSolrApiClient.executePostRequest("modelsdiff", payload);
        LOG.debug("Received models difference response: {}", modelsDiffResponse);

        ObjectMapper objectMapper = new ObjectMapper();
        ModelDiffs modelDiffs = objectMapper.readValue(modelsDiffResponse, ModelDiffs.class);

        // Clear previous mappings before synchronization
        URL_TO_PREFIX.clear();

        for (Diff modelEntry : modelDiffs.getDiffs()) {
            String modelQName = modelEntry.getName();
            String xmlContent = fetchModelXmlContent(modelQName);
            Model model = parseModelXml(xmlContent, modelQName);
            URL_TO_PREFIX.put(model.getQname(), model.getPrefix());
            LOG.debug("Updated URL_TO_PREFIX map with QName: {} and Prefix: {}", model.getQname(), model.getPrefix());
        }

        LOG.debug("Model synchronization complete. Total models synchronized: {}", URL_TO_PREFIX.size());
    }

    /**
     * Fetches the XML content of a model from the Alfresco Solr API.
     *
     * @param modelQName the QName of the model.
     * @return the XML content of the model as a String.
     * @throws Exception if an error occurs while fetching the model XML.
     */
    private String fetchModelXmlContent(String modelQName) throws Exception {
        String endpoint = String.format("model?modelQName=%s", URLEncoder.encode(modelQName, StandardCharsets.UTF_8));
        LOG.debug("Sending GET request to endpoint: {}", endpoint);
        return alfrescoSolrApiClient.executeGetRequest(endpoint);
    }

    /**
     * Parses the model XML to extract its QName and prefix.
     *
     * @param xmlContent the XML content of the model.
     * @param modelQName the QName of the model.
     * @return a Model object with the extracted QName and prefix.
     * @throws Exception if an error occurs while parsing the XML content.
     */
    private Model parseModelXml(String xmlContent, String modelQName) throws Exception {
        LOG.debug("Parsing XML content for model QName: {}", modelQName);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xmlContent));
        Document document = builder.parse(inputSource);
        document.getDocumentElement().normalize();

        Element modelElement = (Element) document.getElementsByTagNameNS("*", "model").item(0);
        String nameAttribute = modelElement.getAttribute("name");

        return new Model()
                .withName(modelQName.substring(modelQName.lastIndexOf("}") + 1))
                .withQname(modelQName.substring(0, modelQName.lastIndexOf("}") + 1))
                .withPrefix(nameAttribute.substring(0, nameAttribute.indexOf(":")));
    }
}