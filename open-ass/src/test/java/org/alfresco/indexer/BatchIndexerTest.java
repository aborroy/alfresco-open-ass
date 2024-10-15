package org.alfresco.indexer;

import org.alfresco.repo.index.AlfrescoService;
import org.alfresco.repo.index.beans.ModelDiffs;
import org.alfresco.testcontainers.AlfrescoContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the BatchIndexer functionality.
 * This class sets up and manages the necessary test containers for Alfresco and OpenSearch.
 */
@SpringJUnitConfig
@SpringBootTest
public class BatchIndexerTest {

    private static final Logger LOG = LoggerFactory.getLogger(BatchIndexerTest.class);

    @Autowired
    private AlfrescoService alfrescoService;

    private static AlfrescoContainer<?> alfrescoContainer;
    private static OpensearchContainer<?> opensearchContainer;

    /**
     * Starts the Alfresco and OpenSearch containers before all tests.
     */
    @BeforeAll
    static void startContainers() {
        LOG.info("Starting Alfresco and OpenSearch containers...");

        alfrescoContainer = new AlfrescoContainer<>("23.3.0")
                .withEnv("CATALINA_OPTS", "-Dsolr.secureComms=secret -Dsolr.sharedSecret=i7wdvtrsfts");
        opensearchContainer = new OpensearchContainer<>("opensearchproject/opensearch:2.17.0");

        alfrescoContainer.start();
        LOG.info("Alfresco container started at: {}:{}", alfrescoContainer.getHost(), alfrescoContainer.getMappedPort(8080));

        opensearchContainer.start();
        LOG.info("OpenSearch container started at: {}:{}", opensearchContainer.getHost(), opensearchContainer.getMappedPort(9200));
    }

    /**
     * Registers dynamic properties for the test environment.
     *
     * @param registry DynamicPropertyRegistry to register properties.
     */
    @DynamicPropertySource
    static void registerDynamicProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("content.service.url", () -> "http://" + alfrescoContainer.getHost() + ":" + alfrescoContainer.getMappedPort(8080));
        registry.add("opensearch.host", opensearchContainer::getHost);
        registry.add("opensearch.port", () -> opensearchContainer.getMappedPort(9200));  // Assuming 9200 is the internal port
    }

    /**
     * Tests the fetching of model diffs from Alfresco service.
     * It asserts that the number of model diffs is as expected.
     */
    @DisplayName("Test fetching model diffs")
    @Test
    void testFetchingModelDiffs() {
        LOG.info("Executing test for fetching model diffs...");

        assertDoesNotThrow(() -> {
            ModelDiffs modelDiffs = alfrescoService.getModelDiffs();
            assertEquals(44, modelDiffs.getDiffs().size(), "Expected 44 model diffs");
            LOG.info("Successfully fetched model diffs with count: {}", modelDiffs.getDiffs().size());
        }, "Exception thrown while fetching model diffs");
    }

    /**
     * Stops the Alfresco and OpenSearch containers after all tests.
     */
    @AfterAll
    static void stopContainers() {
        LOG.info("Stopping Alfresco and OpenSearch containers...");
        alfrescoContainer.stop();
        opensearchContainer.stop();
        LOG.info("Containers stopped successfully.");
    }
}