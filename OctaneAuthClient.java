package com.octane.auth;

import com.octane.config.OctaneConfig;
import okhttp3.*;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles ALM Octane authentication using OkHttp.
 *
 * Two modes:
 *   A) Client ID + Secret  → POST /authentication/sign_in  {"client_id":"..","client_secret":".."}
 *   B) OAuth2 (user+pass)  → POST /authentication/sign_in  {"user":"..","password":".."}
 *
 * OkHttp's {@link JavaNetCookieJar} wraps a {@link CookieManager} and automatically
 * stores the LWSSO_COOKIE_KEY cookie returned by Octane, then re-sends it on
 * every subsequent request — no manual Cookie headers needed.
 */
public class OctaneAuthClient {

    private static final Logger LOG = Logger.getLogger(OctaneAuthClient.class.getName());

    private static final MediaType JSON_MEDIA_TYPE =
            MediaType.get("application/json; charset=utf-8");

    /** In-memory cookie manager that persists cookies for the lifetime of this client. */
    private final CookieManager cookieManager;

    /** Single OkHttpClient instance – must be reused across all API calls. */
    private final OkHttpClient httpClient;

    public OctaneAuthClient() {
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = new OkHttpClient.Builder()
                // JavaNetCookieJar bridges OkHttp ↔ java.net.CookieManager
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60,    TimeUnit.SECONDS)
                .writeTimeout(30,   TimeUnit.SECONDS)
                .build();
    }

    // ------------------------------------------------------------------ //
    //  Option A – Client ID / Secret                                       //
    // ------------------------------------------------------------------ //

    /**
     * Authenticates using an API Client ID and Secret.
     * Octane sets the LWSSO_COOKIE_KEY cookie in the response; OkHttp stores it automatically.
     *
     * @param clientId     API client identifier configured in Octane Admin
     * @param clientSecret Corresponding secret
     * @return LWSSO cookie value (for logging/debugging), null on failure
     */
    public String authenticateWithClientCredentials(String clientId,
                                                    String clientSecret) throws IOException {
        String jsonBody = String.format(
                "{\"client_id\":\"%s\",\"client_secret\":\"%s\"}",
                clientId, clientSecret);
        return doSignIn(jsonBody, "Client-Credentials");
    }

    // ------------------------------------------------------------------ //
    //  Option B – OAuth2 / LWSSO (username + password)                    //
    // ------------------------------------------------------------------ //

    /**
     * Authenticates using OAuth2 user credentials (username + password).
     *
     * @param username Octane user e-mail / login name
     * @param password Corresponding password
     * @return LWSSO cookie value (for logging/debugging), null on failure
     */
    public String authenticateWithOAuth2(String username, String password) throws IOException {
        String jsonBody = String.format(
                "{\"user\":\"%s\",\"password\":\"%s\"}",
                username, password);
        return doSignIn(jsonBody, "OAuth2");
    }

    // ------------------------------------------------------------------ //
    //  Shared sign-in logic                                                //
    // ------------------------------------------------------------------ //

    private String doSignIn(String jsonBody, String mode) throws IOException {
        String url = OctaneConfig.BASE_URL + "/authentication/sign_in";

        RequestBody body    = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
        Request     request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Accept", "application/json")
                .build();

        LOG.info("[Auth-" + mode + "] POST " + url);

        try (Response response = httpClient.newCall(request).execute()) {
            int    status       = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";
            LOG.info("[Auth-" + mode + "] HTTP " + status);

            if (status == 200) {
                String cookieValue = extractLwssoCookieFromHeaders(response);
                if (cookieValue != null) {
                    LOG.info("[Auth-" + mode + "] LWSSO cookie obtained: " + cookieValue.substring(0, 8) + "...");
                } else {
                    LOG.info("[Auth-" + mode + "] Authenticated – cookie stored in CookieJar.");
                }
                return cookieValue;
            } else {
                LOG.severe("[Auth-" + mode + "] Failed. HTTP " + status + " | " + responseBody);
                return null;
            }
        }
    }

    /**
     * Reads LWSSO_COOKIE_KEY directly from Set-Cookie response headers.
     * OkHttp also stores it in the CookieJar in parallel — this is just for visibility.
     */
    private String extractLwssoCookieFromHeaders(Response response) {
        List<String> setCookies = response.headers("Set-Cookie");
        for (String header : setCookies) {
            if (header.startsWith("LWSSO_COOKIE_KEY=")) {
                return header.split(";")[0].split("=", 2)[1];
            }
        }
        return null;
    }

    /**
     * Returns the shared OkHttpClient (with cookie-aware CookieJar).
     * Pass this instance to {@link com.octane.api.OctaneApiClient} so all
     * subsequent requests automatically carry the session cookie.
     */
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * Returns the underlying CookieManager for inspection if needed.
     */
    public CookieManager getCookieManager() {
        return cookieManager;
    }
}
