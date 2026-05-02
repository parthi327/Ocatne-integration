package com.octane.api;

import com.octane.config.OctaneConfig;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * All ALM Octane REST API operations implemented with OkHttp.
 *
 * This client receives the shared {@link OkHttpClient} from
 * {@link com.octane.auth.OctaneAuthClient}. Because that client was built with
 * a {@link JavaNetCookieJar}, every request automatically carries the
 * LWSSO_COOKIE_KEY session cookie — no manual header injection needed.
 *
 * Entity hierarchy:
 *   Test Suite ──► Suite Run ──► Automation Test (AT)
 *                                   └─► Automation Run (AR)
 */
public class OctaneApiClient {

    private static final Logger LOG = Logger.getLogger(OctaneApiClient.class.getName());

    private static final MediaType JSON      = MediaType.get("application/json; charset=utf-8");
    private static final MediaType OCTET     = MediaType.get("application/octet-stream");

    private final OkHttpClient client;
    private final String       apiRoot;   // e.g. https://host/api/shared_spaces/1001/workspaces/1002

    public OctaneApiClient(OkHttpClient client) {
        this.client  = client;
        this.apiRoot = OctaneConfig.apiRoot();
    }

    // ------------------------------------------------------------------ //
    //  1. Create Automation Test (AT) for a given Test Suite              //
    // ------------------------------------------------------------------ //

    /**
     * Creates an Automation Test entity linked to the specified test suite.
     *
     * @param suiteId   Octane test suite entity ID
     * @param testName  Display name of the automation test
     * @param framework Framework list-node ID, e.g. "selenium", "testng"
     * @param language  Language list-node ID, e.g. "Java"
     * @return Raw JSON response body (contains the created AT's "id")
     */
    public String createAutomationTest(String suiteId, String testName,
                                       String framework, String language) throws IOException {
        String url  = apiRoot + "/test_suites/" + suiteId + "/automation_tests";
        String body =
                "{\n" +
                "  \"data\": [{\n" +
                "    \"name\":      \"" + testName  + "\",\n" +
                "    \"framework\": {\"id\": \"" + framework + "\"},\n" +
                "    \"language\":  {\"id\": \"" + language  + "\"}\n" +
                "  }]\n" +
                "}";
        return post(url, body, "Create Automation Test");
    }

    // ------------------------------------------------------------------ //
    //  2. Create Suite Run for a given Test Suite                         //
    // ------------------------------------------------------------------ //

    /**
     * Creates a Suite Run container under the given test suite.
     *
     * @param suiteId     Parent test suite entity ID
     * @param runName     Display name for this suite run
     * @param environment Environment list-node ID (pass null to omit)
     * @return Raw JSON response body (contains the created suite_run "id")
     */
    public String createSuiteRun(String suiteId, String runName,
                                  String environment) throws IOException {
        String url = apiRoot + "/suite_runs";

        StringBuilder body = new StringBuilder();
        body.append("{\n  \"data\": [{\n");
        body.append("    \"name\": \"").append(runName).append("\",\n");
        body.append("    \"test_suite\": {\"type\": \"test_suite\", \"id\": \"").append(suiteId).append("\"}");
        if (environment != null && !environment.isBlank()) {
            body.append(",\n    \"environment\": {\"type\": \"list_node\", \"id\": \"")
                .append(environment).append("\"}");
        }
        body.append("\n  }]\n}");

        return post(url, body.toString(), "Create Suite Run");
    }

    // ------------------------------------------------------------------ //
    //  3. Create Automation Run (AR) inside a Suite Run                   //
    // ------------------------------------------------------------------ //

    /**
     * Creates an Automation Run (AR) linked to an AT inside a Suite Run.
     *
     * @param suiteRunId       Parent suite run entity ID
     * @param automationTestId Automation test (AT) entity ID
     * @param runName          Display name for this run record
     * @return Raw JSON response body (contains the created run "id")
     */
    public String createAutomationRun(String suiteRunId, String automationTestId,
                                       String runName) throws IOException {
        String url  = apiRoot + "/runs";
        String body =
                "{\n" +
                "  \"data\": [{\n" +
                "    \"type\":      \"run_automated\",\n" +
                "    \"name\":      \"" + runName          + "\",\n" +
                "    \"suite_run\": {\"type\": \"suite_run\",      \"id\": \"" + suiteRunId       + "\"},\n" +
                "    \"test\":      {\"type\": \"test_automated\",  \"id\": \"" + automationTestId + "\"}\n" +
                "  }]\n" +
                "}";
        return post(url, body, "Create Automation Run");
    }

    // ------------------------------------------------------------------ //
    //  4. Update Automation Run Status                                     //
    // ------------------------------------------------------------------ //

    /**
     * Updates the native status and duration of an Automation Run.
     *
     * Common nativeStatusId values (verify with your Octane metadata API):
     *   "list_node.run_native_status.passed"
     *   "list_node.run_native_status.failed"
     *   "list_node.run_native_status.skipped"
     *   "list_node.run_native_status.planned"
     *
     * @param runId          AR entity ID
     * @param nativeStatusId Octane list_node logical name for the status
     * @param durationMs     Test execution duration in milliseconds
     * @return Raw JSON response body of the updated run
     */
    public String updateRunStatus(String runId, String nativeStatusId,
                                   long durationMs) throws IOException {
        String url  = apiRoot + "/runs/" + runId;
        String body =
                "{\n" +
                "  \"native_status\": {\"type\": \"list_node\", \"id\": \"" + nativeStatusId + "\"},\n" +
                "  \"duration\": " + durationMs + "\n" +
                "}";
        return put(url, body, "Update Run Status");
    }

    // ------------------------------------------------------------------ //
    //  5. Upload Attachment at Suite Run level                            //
    // ------------------------------------------------------------------ //

    /**
     * Uploads a file as a multipart attachment to an existing Suite Run.
     *
     * Octane attachment endpoint:
     *   POST /suite_runs/{id}/attachments
     *   Content-Type: multipart/form-data
     *   Parts: "file" (binary), "entity_type" (text), "entity_id" (text)
     *
     * @param suiteRunId Suite Run entity ID
     * @param file       File to attach (e.g. surefire XML, screenshot)
     * @return Raw JSON response body of the created attachment entity
     */
    public String uploadAttachmentToSuiteRun(String suiteRunId, File file) throws IOException {
        String url = apiRoot + "/suite_runs/" + suiteRunId + "/attachments";

        RequestBody multipart = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(),
                        RequestBody.create(file, OCTET))
                .addFormDataPart("entity_type", "suite_run")
                .addFormDataPart("entity_id",   suiteRunId)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(multipart)
                // Required by Octane for binary/preview endpoints
                .header("HPECLIENTTYPE", "HPE_REST_API_TECH_PREVIEW")
                .header("Accept", "application/json")
                .build();

        LOG.info("[Upload Attachment] POST " + url + " | file=" + file.getName());
        try (Response response = client.newCall(request).execute()) {
            return processResponse(response, "Upload Attachment");
        }
    }

    // ------------------------------------------------------------------ //
    //  Internal helpers                                                    //
    // ------------------------------------------------------------------ //

    /** Executes a JSON POST and returns the response body. */
    private String post(String url, String jsonBody, String operation) throws IOException {
        RequestBody body    = RequestBody.create(jsonBody, JSON);
        Request     request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .build();

        LOG.info("[" + operation + "] POST " + url);
        LOG.fine("[" + operation + "] Body: " + jsonBody);

        try (Response response = client.newCall(request).execute()) {
            return processResponse(response, operation);
        }
    }

    /** Executes a JSON PUT and returns the response body. */
    private String put(String url, String jsonBody, String operation) throws IOException {
        RequestBody body    = RequestBody.create(jsonBody, JSON);
        Request     request = new Request.Builder()
                .url(url)
                .put(body)
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .build();

        LOG.info("[" + operation + "] PUT " + url);
        LOG.fine("[" + operation + "] Body: " + jsonBody);

        try (Response response = client.newCall(request).execute()) {
            return processResponse(response, operation);
        }
    }

    /** Reads body and validates HTTP status; throws on non-2xx. */
    private String processResponse(Response response, String operation) throws IOException {
        int    status = response.code();
        String body   = response.body() != null ? response.body().string() : "";

        LOG.info("[" + operation + "] HTTP " + status);

        if (status >= 200 && status < 300) {
            LOG.info("[" + operation + "] Success.");
            return body;
        }

        String msg = "[" + operation + "] Failed. HTTP " + status + " | " + body;
        LOG.severe(msg);
        throw new IOException(msg);
    }
}
