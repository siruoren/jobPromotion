package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsRemoteClient {

    private static final Logger LOGGER = Logger.getLogger(JenkinsRemoteClient.class.getName());
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;
    private static final int MAX_REDIRECTS = 5;

    private static volatile boolean sslInitialized = false;

    private final String baseUrl;
    private final String username;
    private final String password;
    private String crumbValue;
    private String crumbHeader = "Jenkins-Crumb";

    static {
        initTrustAllSSL();
    }

    private static void initTrustAllSSL() {
        if (sslInitialized) return;
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            sslInitialized = true;
            LOGGER.log(Level.INFO, "SSL trust-all initialized for HTTPS support");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to initialize SSL trust manager", e);
        }
    }

    public JenkinsRemoteClient(@NonNull String baseUrl, @NonNull String username, @NonNull String password) {
        String url = baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url;
        this.username = username;
        this.password = password;
    }

    private void ensureCrumb() throws IOException {
        if (crumbValue != null) {
            return;
        }
        try {
            String crumbUrl = baseUrl + "/crumbIssuer/api/json";
            LOGGER.log(Level.INFO, "Fetching crumb from: " + crumbUrl);
            HttpResponseResult result = doHttpGet(crumbUrl);
            LOGGER.log(Level.INFO, "Crumb request returned HTTP " + result.responseCode);

            if (result.responseCode == 200 && result.body.trim().startsWith("{")) {
                try {
                    net.sf.json.JSONObject obj = net.sf.json.JSONObject.fromObject(result.body);
                    this.crumbValue = obj.optString("crumb", null);
                    this.crumbHeader = obj.optString("crumbRequestField", "Jenkins-Crumb");
                    LOGGER.log(Level.INFO, "Obtained crumb, header: " + crumbHeader);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to parse crumb response", e);
                }
            } else {
                LOGGER.log(Level.INFO, "Crumb issuer not available or auth failed (HTTP " + result.responseCode + "), proceeding without crumb");
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to fetch crumb, proceeding without: " + e.getMessage());
        }
    }

    public boolean testConnection() throws IOException {
        ensureCrumb();
        String url = baseUrl + "/api/json?tree=mode";
        try {
            HttpResponseResult result = doHttpGet(url);
            LOGGER.log(Level.INFO, "Test connection to " + url + " returned HTTP " + result.responseCode);
            if (result.responseCode == 401 || result.responseCode == 403) {
                LOGGER.log(Level.WARNING, "Authentication failed with HTTP " + result.responseCode);
                return false;
            }
            return result.responseCode == 200 && result.body.trim().startsWith("{");
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Test connection failed: " + e.getMessage(), e);
            throw e;
        }
    }

    public List<RemoteJobInfo> listJobs(String folderPath) throws IOException {
        ensureCrumb();

        String apiUrl;
        if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
            apiUrl = baseUrl + "/api/json?tree=jobs[name,url,color,_class]";
        } else {
            String encodedPath = encodeFolderPath(folderPath);
            apiUrl = baseUrl + "/job/" + encodedPath + "/api/json?tree=jobs[name,url,color,_class]";
        }

        LOGGER.log(Level.INFO, "Listing remote jobs from: " + apiUrl);

        HttpResponseResult result = doHttpGet(apiUrl);
        LOGGER.log(Level.INFO, "List jobs request returned HTTP " + result.responseCode);

        if (result.responseCode == 401) {
            throw new IOException("Authentication failed (HTTP 401). Please use API Token instead of password (Jenkins > User > Configure > API Token)");
        }
        if (result.responseCode == 403) {
            throw new IOException("Access denied (HTTP 403). The user does not have permission to access the remote Jenkins API.");
        }
        if (result.responseCode == 404) {
            throw new IOException("Folder not found (HTTP 404). Please check the folder path: " + folderPath);
        }
        if (result.responseCode == 500) {
            throw new IOException("Remote Jenkins server error (HTTP 500). The remote Jenkins may be overloaded or the folder path is invalid.");
        }

        String body = result.body;
        if (body.trim().startsWith("<!") || body.trim().startsWith("<html") || body.trim().startsWith("<HTML")) {
            LOGGER.log(Level.WARNING, "Received HTML response instead of JSON. HTTP code: " + result.responseCode);
            throw new IOException("Remote Jenkins returned HTML page (HTTP " + result.responseCode + "). Please use API Token instead of password.");
        }

        List<RemoteJobInfo> topJobs = parseJobsFromJson(body, folderPath != null ? folderPath : "");

        List<RemoteJobInfo> allJobs = new ArrayList<>();
        for (RemoteJobInfo job : topJobs) {
            allJobs.add(job);
            if (job.isFolder()) {
                try {
                    List<RemoteJobInfo> subJobs = listSubJobs(job.getFullDisplayName());
                    allJobs.addAll(subJobs);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to list sub-jobs for folder: " + job.getFullDisplayName() + ", " + e.getMessage());
                }
            }
        }

        return allJobs;
    }

    private List<RemoteJobInfo> listSubJobs(String folderPath) throws IOException {
        String encodedPath = encodeFolderPath(folderPath);
        String apiUrl = baseUrl + "/job/" + encodedPath + "/api/json?tree=jobs[name,url,color,_class]";
        LOGGER.log(Level.INFO, "Listing sub-jobs from: " + apiUrl);

        HttpResponseResult result = doHttpGet(apiUrl);

        if (result.responseCode != 200) {
            LOGGER.log(Level.WARNING, "Failed to list sub-jobs for " + folderPath + ", HTTP " + result.responseCode);
            return new ArrayList<>();
        }

        List<RemoteJobInfo> subJobs = parseJobsFromJson(result.body, folderPath);

        List<RemoteJobInfo> allSubJobs = new ArrayList<>();
        for (RemoteJobInfo job : subJobs) {
            allSubJobs.add(job);
            if (job.isFolder()) {
                try {
                    allSubJobs.addAll(listSubJobs(job.getFullDisplayName()));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to list deeper sub-jobs for: " + job.getFullDisplayName());
                }
            }
        }

        return allSubJobs;
    }

    public String getJobConfig(String fullJobPath) throws IOException {
        ensureCrumb();

        String encodedPath = encodeFolderPath(fullJobPath);
        String apiUrl = baseUrl + "/job/" + encodedPath + "/config.xml";
        LOGGER.log(Level.INFO, "Fetching job config from: " + apiUrl);
        HttpResponseResult result = doHttpGet(apiUrl);

        if (result.responseCode == 401) {
            throw new IOException("Authentication failed (HTTP 401) when fetching job config.");
        }
        if (result.responseCode == 404) {
            throw new IOException("Job not found (HTTP 404): " + fullJobPath);
        }

        return result.body;
    }

    private String getBasicAuth() {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private static class HttpResponseResult {
        final int responseCode;
        final String body;
        final String redirectLocation;

        HttpResponseResult(int responseCode, String body, String redirectLocation) {
            this.responseCode = responseCode;
            this.body = body;
            this.redirectLocation = redirectLocation;
        }
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("Authorization", getBasicAuth());
        if (crumbValue != null) {
            conn.setRequestProperty(crumbHeader, crumbValue);
        }
        conn.setInstanceFollowRedirects(false);
        return conn;
    }

    private HttpResponseResult doHttpGet(String urlStr) throws IOException {
        String currentUrl = urlStr;
        int redirectCount = 0;

        while (redirectCount < MAX_REDIRECTS) {
            HttpURLConnection conn = openConnection(currentUrl);
            try {
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");

                int responseCode = conn.getResponseCode();
                LOGGER.log(Level.INFO, "HTTP GET " + currentUrl + " -> " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                        || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                        || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String location = conn.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new IOException("Redirect (HTTP " + responseCode + ") without Location header");
                    }
                    if (!location.startsWith("http")) {
                        URL base = new URL(currentUrl);
                        location = new URL(base, location).toString();
                    }
                    LOGGER.log(Level.INFO, "Redirect (" + responseCode + ") " + currentUrl + " -> " + location);
                    currentUrl = location;
                    redirectCount++;
                    continue;
                }

                InputStream is;
                if (responseCode >= 200 && responseCode < 300) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }

                String body = "";
                if (is != null) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                    }
                    body = sb.toString();
                }

                return new HttpResponseResult(responseCode, body, conn.getHeaderField("Location"));
            } finally {
                conn.disconnect();
            }
        }

        throw new IOException("Too many redirects (>" + MAX_REDIRECTS + ") for URL: " + urlStr);
    }

    private String encodeFolderPath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        String[] parts = path.split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append("/job/");
            }
            sb.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private List<RemoteJobInfo> parseJobsFromJson(String json, String parentPath) {
        List<RemoteJobInfo> result = new ArrayList<>();
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                LOGGER.log(Level.WARNING, "Response is not valid JSON, starts with: " + trimmed.substring(0, Math.min(200, trimmed.length())));
                return result;
            }

            net.sf.json.JSONObject obj = net.sf.json.JSONObject.fromObject(json);
            net.sf.json.JSONArray jobs = obj.optJSONArray("jobs");
            if (jobs == null) {
                return result;
            }
            for (int i = 0; i < jobs.size(); i++) {
                net.sf.json.JSONObject job = jobs.getJSONObject(i);
                String name = job.optString("name", "");
                String fullDisplayName = parentPath.isEmpty() ? name : parentPath + "/" + name;
                String color = job.optString("color", "");

                String clazz = job.optString("_class", "");
                boolean isFolder = clazz.contains("Folder") || clazz.contains("folder");

                result.add(new RemoteJobInfo(name, fullDisplayName, isFolder, color));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse jobs JSON", e);
        }
        return result;
    }
}
