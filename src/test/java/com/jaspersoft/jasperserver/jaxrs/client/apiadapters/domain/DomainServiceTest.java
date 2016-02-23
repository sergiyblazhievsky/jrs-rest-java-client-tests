package com.jaspersoft.jasperserver.jaxrs.client.apiadapters.domain;

import com.jaspersoft.jasperserver.dto.resources.ClientFolder;
import com.jaspersoft.jasperserver.dto.resources.ClientResource;
import com.jaspersoft.jasperserver.dto.resources.ClientResourceListWrapper;
import com.jaspersoft.jasperserver.dto.resources.ClientResourceLookup;
import com.jaspersoft.jasperserver.dto.resources.ResourceMediaType;
import com.jaspersoft.jasperserver.dto.resources.domain.ClientDomain;
import com.jaspersoft.jasperserver.jaxrs.client.RestClientTestUtil;
import com.jaspersoft.jasperserver.jaxrs.client.apiadapters.importexport.importservice.ImportParameter;
import com.jaspersoft.jasperserver.jaxrs.client.apiadapters.resources.ResourceSearchParameter;
import com.jaspersoft.jasperserver.jaxrs.client.core.exceptions.JSClientWebException;
import com.jaspersoft.jasperserver.jaxrs.client.core.operationresult.OperationResult;
import com.jaspersoft.jasperserver.jaxrs.client.dto.importexport.StateDto;
import org.apache.log4j.Logger;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

/**
 * @author Tetiana Iefimenko
 */
public class DomainServiceTest extends RestClientTestUtil {

    private static final String RESOURCES_LOCAL_FOLDER = "src/test/resources/imports/domains";
    private static final String EXPORT_SERVER_URI = "/temp/exportResources";
    private static final String DESTINATION_COPY_URI = "/temp/DomainsRestCopies";
    private static final String DESTINATION_COPY_LABEL = "DomainsRestCopies";

    private static final String INPROGRESS_STATUS = "inprogress";
    public static final Logger TEST_LOGGER = Logger.getLogger(DomainServiceTest.class.getName());

    @BeforeGroups(groups = {"domains"})
    public void before() {
        initClient();
        initSession();
        session.getStorage().getConfiguration().setHandleErrors(false);
        TEST_LOGGER.debug("Start to create test folders on server");
        createTestResource();
        TEST_LOGGER.debug("Test folders were created successfully");
        /*
        * Upload source data to server
        * */
        TEST_LOGGER.debug("Start to load test resources to server");
        try {
            loadTestResources(RESOURCES_LOCAL_FOLDER);
            TEST_LOGGER.debug("Test resources was loaded successfully");
        } catch (Exception e) {
            TEST_LOGGER.debug("Test resources were not loaded resources of exception", e);
        }
    }

    @Test(groups = {"domains"})
    public void should_get_copy_and_compare_domains() throws URISyntaxException, InterruptedException {

        /*
        * Get all domains as resources from server
        * */

        ClientResourceListWrapper list = session
                .resourcesService()
                .resources()
                .parameter(ResourceSearchParameter.FOLDER_URI, EXPORT_SERVER_URI)
                .parameter(ResourceSearchParameter.TYPE, ResourceMediaType.SEMANTIC_LAYER_DATA_SOURCE_CLIENT_TYPE)
                .search()
                .getEntity();
        Map<String, String> resultMap = new HashMap<String, String>();
        final List<ClientResourceLookup> resourceLookups = list.getResourceLookups();
        TEST_LOGGER.info("Testing domains. Domains count: " + resourceLookups.size());
        for (ClientResourceLookup resourceLookup : resourceLookups) {

            /*
            * Each resource get as domain, clone, post to server, get posted clone and compare with uploaded domain
            * */
            final String uri = resourceLookup.getUri();
            TEST_LOGGER.debug("Testing domain: " + uri);
            String result = executeTest(resourceLookup);
            if (result != null) {
                resultMap.put(uri, result);
                if (TEST_LOGGER.isDebugEnabled()) {
                    TEST_LOGGER.debug("FAILED: " + result);
                } else {
                    TEST_LOGGER.info("FAILED " + uri);
                }
            } else {
                TEST_LOGGER.info("PASSED " + uri);
            }

        }
        assertEquals(resultMap.size(), 0, "Failed domains: " + resultMap.keySet().toString());
    }

    private String executeTest(ClientResourceLookup clientResourceLookup) throws URISyntaxException, InterruptedException {
        final String uri = clientResourceLookup.getUri();
            /*
            * Get domain form server
            * */
        ClientDomain domain;
        OperationResult<ClientDomain> operationResult = session
                .domainService()
                .domain(uri)
                .get();
        try {
            domain = operationResult.getEntity();
            TEST_LOGGER.debug("GET passed");
        } catch (JSClientWebException e) {
            TEST_LOGGER.debug("GET failed. Error code:" + operationResult.getResponseStatus());
            if (TEST_LOGGER.isTraceEnabled())
                TEST_LOGGER.trace(operationResult.getSerializedContent());
            TEST_LOGGER.debug("COPY skipped");
            TEST_LOGGER.debug("GET copy skipped");
            TEST_LOGGER.debug("Comparison skipped");
            return operationResult.getSerializedContent();
        }
        domain.setSecurityFile(null);
        domain.setBundles(null);

        /*
        *
        * Clone domain*/
        ClientDomain clonedDomain = new ClientDomain(domain);

        /*
        * Post domain to server
        * */

        String newUri = uri.replace(EXPORT_SERVER_URI, DESTINATION_COPY_URI);
        try {
            operationResult = session
                    .domainService()
                    .domain(newUri)
                    .update(clonedDomain);
            operationResult.getEntity();
            TEST_LOGGER.debug("COPY passed");
        } catch (Exception e) {
            TEST_LOGGER.debug("COPY failed. Error code: " + operationResult.getResponseStatus());
            if (TEST_LOGGER.isTraceEnabled())
                TEST_LOGGER.trace(operationResult.getSerializedContent());
            TEST_LOGGER.debug("GET copy skipped");
            TEST_LOGGER.debug("Comparison skipped");
            return operationResult.getSerializedContent();
        }
        /*
        * Get cloned domain from server
        * */

        ClientDomain retrievedDomain;

        operationResult = session
                .domainService()
                .domain(newUri)
                .get();
        try {
            retrievedDomain = operationResult.getEntity();
            TEST_LOGGER.debug("GET copy passed");
        } catch (JSClientWebException e) {
            TEST_LOGGER.debug("GET copy failed. Error code: " + operationResult.getResponseStatus());
            if (TEST_LOGGER.isTraceEnabled())
                TEST_LOGGER.trace(operationResult.getSerializedContent());
            TEST_LOGGER.debug("Comparison skipped");
            return operationResult.getSerializedContent();
        }
        domain.setCreationDate(null);
        domain.setUpdateDate(null);
        domain.setUri(null);

        retrievedDomain.setCreationDate(null);
        retrievedDomain.setUpdateDate(null);
        retrievedDomain.setUri(null);

        if (domain.equals(retrievedDomain)) {
            TEST_LOGGER.debug("Comparison passed");
            return null;
        } else {
            TEST_LOGGER.debug("Comparison failed");
            return "Domains are not equal";
        }
    }

    private void loadTestResources(String folderName) throws URISyntaxException, InterruptedException {
        TEST_LOGGER.debug("Start to scan import local folder");
        File folder = new File(folderName);
        File[] listOfResources = folder.listFiles();
        if (listOfResources.length > 0) {
            TEST_LOGGER.debug("Import. Catalogs count: " + listOfResources.length);
        } else {
            TEST_LOGGER.debug("Import catalogs were not found");
            return;
        }
        if (listOfResources.length > 0) {
            for (File resource : listOfResources) {
                OperationResult<StateDto> operationResult = session
                        .importService()
                        .newTask()
                        .parameter(ImportParameter.INCLUDE_ACCESS_EVENTS, true)
                        .create(resource);

                StateDto stateDto = operationResult.getEntity();

                while (stateDto.getPhase().equals(INPROGRESS_STATUS)) {
                    stateDto = session
                            .importService()
                            .task(stateDto.getId())
                            .state().getEntity();
                    Thread.sleep(100);
                }
                TEST_LOGGER.debug("Import completed: " + resource.getName());
            }
        }
    }

    private void createTestResource() {

        ClientFolder folder = new ClientFolder();
        folder
                .setUri(DESTINATION_COPY_URI)
                .setLabel(DESTINATION_COPY_LABEL)
                .setDescription("Test folder")
                .setVersion(0);

        OperationResult<ClientResource> operationResult = session
                .resourcesService()
                .resource(folder.getUri())
                .createOrUpdate(folder);
        if (operationResult.getResponse().getStatus() == 200) {
            TEST_LOGGER.debug("Test folder " + DESTINATION_COPY_URI + " was created successfully");
        }

    }

    @AfterGroups(groups = {"domains"})
    public void after() {
        session
                .resourcesService()
                .resources()
                .parameter(ResourceSearchParameter.RESOURCE_URI, DESTINATION_COPY_URI)
                .parameter(ResourceSearchParameter.RESOURCE_URI, EXPORT_SERVER_URI)
                .delete();
        session.logout();
    }
}
