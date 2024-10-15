package org.alfresco.indexer;

import org.alfresco.repo.index.AlfrescoService;
import org.alfresco.repo.index.beans.Diff;
import org.alfresco.repo.index.beans.Model;
import org.alfresco.repo.index.beans.ModelDiffs;
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
import java.util.Map;
import java.util.TreeMap;

/**
 * Service class responsible for managing model-to-prefix mappings.
 * Synchronizes model definitions from Alfresco Solr API and populates
 * a map where each model's QName is mapped to its associated prefix.
 */
@Service
public class ModelMappingIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMappingIndexer.class);

    @Autowired
    private AlfrescoService alfrescoService;

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

        // Fetch the model diffs from Alfresco
        ModelDiffs modelDiffs = alfrescoService.getModelDiffs();

        // Clear previous mappings before synchronization
        LOG.debug("Clearing existing URL_TO_PREFIX mappings.");
        URL_TO_PREFIX.clear();

        // Process each model diff
        for (Diff modelEntry : modelDiffs.getDiffs()) {
            String modelQName = modelEntry.getName();
            try {
                // Fetch and parse the model's XML content
                LOG.debug("Fetching and parsing XML content for model QName: {}", modelQName);
                String xmlContent = alfrescoService.getModelXmlContent(modelQName);
                Model model = parseModelXml(xmlContent, modelQName);

                // Update the model mappings with QName and prefix
                URL_TO_PREFIX.put(model.getQname(), model.getPrefix());
                LOG.debug("Updated URL_TO_PREFIX map with QName: {} and Prefix: {}", model.getQname(), model.getPrefix());
            } catch (Exception e) {
                LOG.error("Error processing model QName: {}. Skipping model. Error: {}", modelQName, e.getMessage(), e);
            }
        }

        LOG.info("Model synchronization complete. Total models synchronized: {}", URL_TO_PREFIX.size());
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

        // Extract the model element from the XML document
        Element modelElement = (Element) document.getElementsByTagNameNS("*", "model").item(0);
        String nameAttribute = modelElement.getAttribute("name");

        // Build and return the Model object
        String prefix = nameAttribute.substring(0, nameAttribute.indexOf(":"));
        String qname = modelQName.substring(0, modelQName.lastIndexOf("}") + 1);
        String name = modelQName.substring(modelQName.lastIndexOf("}") + 1);
        LOG.debug("Successfully parsed model QName: {} with prefix: {}", qname, prefix);

        return new Model().withName(name).withQname(qname).withPrefix(prefix);
    }
}