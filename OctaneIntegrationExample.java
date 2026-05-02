package com.octane.example;

import com.octane.service.OctaneService;
import com.octane.util.OctaneResponseParser;

import java.io.File;

/**
 * End-to-end demonstration of the Octane integration:
 *
 *   1. Authenticate (two options shown)
 *   2. Create Automation Test for a suite
 *   3. Create Suite Run in that suite
 *   4. Create Automation Run inside the Suite Run
 *   5. Update run status
 *   6. Upload attachment to the Suite Run
 *
 * Replace the placeholder values with your real Octane data.
 */
public class OctaneIntegrationExample {

    public static void main(String[] args) throws Exception {

        // ── Configuration ────────────────────────────────────────────────
        final String SUITE_ID    = "100";          // Your Test Suite entity ID
        final String ATTACHMENT  = "target/surefire-reports/TestNG-Results.xml";

        // ── Open service ─────────────────────────────────────────────────
        // OkHttpClient is long-lived; no close() needed.
        OctaneService octane = new OctaneService();

            // ── Step 1A: Authenticate with Client ID/Secret ───────────────
            System.out.println("=== Step 1A: Authenticate with Client Credentials ===");
            octane.loginWithClientCredentials(
                    System.getenv("OCTANE_CLIENT_ID"),
                    System.getenv("OCTANE_CLIENT_SECRET")
            );

            // ── (Alternative) Step 1B: Authenticate with OAuth2 ──────────
            // System.out.println("=== Step 1B: Authenticate with OAuth2 ===");
            // octane.loginWithOAuth2(
            //         System.getenv("OCTANE_OAUTH2_USER"),
            //         System.getenv("OCTANE_OAUTH2_PASS")
            // );

            // ── Step 2: Create Automation Test ────────────────────────────
            System.out.println("\n=== Step 2: Create Automation Test ===");
            String atJson = octane.createAutomationTest(
                    SUITE_ID,
                    "Login Smoke Test",
                    "selenium",     // framework list-node id
                    "Java"          // language  list-node id
            );
            System.out.println("Response: " + atJson);
            String atId = OctaneResponseParser.extractId(atJson);
            System.out.println("Created AT id: " + atId);

            // ── Step 3: Create Suite Run ───────────────────────────────────
            System.out.println("\n=== Step 3: Create Suite Run ===");
            String srJson = octane.createSuiteRun(
                    SUITE_ID,
                    "Nightly Automation Run – " + java.time.LocalDate.now(),
                    null            // environment (pass list_node ID or null)
            );
            System.out.println("Response: " + srJson);
            String suiteRunId = OctaneResponseParser.extractId(srJson);
            System.out.println("Created Suite Run id: " + suiteRunId);

            // ── Step 4: Create Automation Run ─────────────────────────────
            System.out.println("\n=== Step 4: Create Automation Run ===");
            String arJson = octane.createAutomationRun(
                    suiteRunId,
                    atId,
                    "Login Smoke Test – Run 1"
            );
            System.out.println("Response: " + arJson);
            String runId = OctaneResponseParser.extractId(arJson);
            System.out.println("Created AR id: " + runId);

            // ── Step 5: Update Run Status ─────────────────────────────────
            System.out.println("\n=== Step 5: Update Run Status → PASSED ===");
            String updateJson = octane.updateRunStatus(
                    runId,
                    "list_node.run_native_status.passed",  // passed / failed / skipped
                    4200L                                  // duration in ms
            );
            System.out.println("Response: " + updateJson);

            // ── Step 6: Upload Attachment to Suite Run ────────────────────
            System.out.println("\n=== Step 6: Upload Attachment ===");
            File report = new File(ATTACHMENT);
            if (report.exists()) {
                String attachJson = octane.uploadAttachment(suiteRunId, report);
                System.out.println("Response: " + attachJson);
            } else {
                System.out.println("Attachment file not found at: " + ATTACHMENT
                        + " – skipping upload.");
            }

            System.out.println("\n=== All steps completed successfully ===");
    }
}
