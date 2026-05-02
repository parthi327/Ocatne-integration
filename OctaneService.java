package com.octane.service;

import com.octane.api.OctaneApiClient;
import com.octane.auth.OctaneAuthClient;
import com.octane.config.OctaneConfig;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * High-level facade that combines authentication and API operations.
 *
 * The underlying OkHttpClient is long-lived and thread-safe — no need to close it.
 *
 * Usage:
 * <pre>
 *   OctaneService svc = new OctaneService();
 *
 *   // Option A
 *   svc.loginWithClientCredentials("client-id", "client-secret");
 *   // — or Option B —
 *   svc.loginWithOAuth2("user@company.com", "password");
 *
 *   String srJson = svc.createSuiteRun("100", "Nightly Run", null);
 *   String atJson = svc.createAutomationTest("100", "Login Test", "selenium", "Java");
 *   // parse IDs, then:
 *   String arJson = svc.createAutomationRun(suiteRunId, atId, "Login Test Run");
 *   svc.updateRunStatus(arId, "list_node.run_native_status.passed", 3500L);
 *   svc.uploadAttachment(suiteRunId, new File("test-results.xml"));
 * </pre>
 */
public class OctaneService {

    private static final Logger LOG = Logger.getLogger(OctaneService.class.getName());

    private final OctaneAuthClient authClient;
    private OctaneApiClient apiClient;

    public OctaneService() {
        this.authClient = new OctaneAuthClient();
    }

    // ------------------------------------------------------------------ //
    //  Authentication                                                      //
    // ------------------------------------------------------------------ //

    /** Option A: Authenticate with Client ID + Secret. */
    public void loginWithClientCredentials(String clientId, String clientSecret) throws IOException {
        String result = authClient.authenticateWithClientCredentials(clientId, clientSecret);
        if (result == null) throw new IOException("Authentication failed: no cookie returned.");
        this.apiClient = new OctaneApiClient(authClient.getHttpClient());
        LOG.info("Authenticated via Client Credentials.");
    }

    /** Option B: Authenticate with OAuth2 (username + password). */
    public void loginWithOAuth2(String username, String password) throws IOException {
        String result = authClient.authenticateWithOAuth2(username, password);
        if (result == null) throw new IOException("Authentication failed: no cookie returned.");
        this.apiClient = new OctaneApiClient(authClient.getHttpClient());
        LOG.info("Authenticated via OAuth2.");
    }

    // ------------------------------------------------------------------ //
    //  API Operations (delegates to OctaneApiClient)                      //
    // ------------------------------------------------------------------ //

    /** Creates an Automation Test linked to a test suite. */
    public String createAutomationTest(String suiteId, String testName,
                                        String framework, String language) throws IOException {
        ensureAuthenticated();
        return apiClient.createAutomationTest(suiteId, testName, framework, language);
    }

    /** Creates a Suite Run under a given test suite. */
    public String createSuiteRun(String suiteId, String runName,
                                  String environment) throws IOException {
        ensureAuthenticated();
        return apiClient.createSuiteRun(suiteId, runName, environment);
    }

    /** Creates an Automation Run (AR) inside an existing Suite Run. */
    public String createAutomationRun(String suiteRunId, String automationTestId,
                                       String runName) throws IOException {
        ensureAuthenticated();
        return apiClient.createAutomationRun(suiteRunId, automationTestId, runName);
    }

    /**
     * Updates the status of an Automation Run.
     *
     * Common nativeStatusId values:
     *   "list_node.run_native_status.passed"
     *   "list_node.run_native_status.failed"
     *   "list_node.run_native_status.skipped"
     */
    public String updateRunStatus(String runId, String nativeStatusId,
                                   long durationMs) throws IOException {
        ensureAuthenticated();
        return apiClient.updateRunStatus(runId, nativeStatusId, durationMs);
    }

    /** Uploads a file attachment to an existing Suite Run. */
    public String uploadAttachment(String suiteRunId, File file) throws IOException {
        ensureAuthenticated();
        return apiClient.uploadAttachmentToSuiteRun(suiteRunId, file);
    }

    // ------------------------------------------------------------------ //
    //  Internal                                                            //
    // ------------------------------------------------------------------ //

    private void ensureAuthenticated() {
        if (apiClient == null) {
            throw new IllegalStateException(
                    "Not authenticated. Call loginWithClientCredentials() or loginWithOAuth2() first.");
        }
    }
}
