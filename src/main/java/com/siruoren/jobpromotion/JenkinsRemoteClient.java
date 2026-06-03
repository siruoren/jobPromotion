package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsRemoteClient {

    private static final Logger LOGGER = Logger.getLogger(JenkinsRemoteClient.class.getName());
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private final String baseUrl;
    private final String username;
    private final String password;

    public JenkinsRemoteClient(@NonNull String baseUrl, @NonNull String username, @NonNull String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
    }

    public boolean testConnection() throws IOException {
        String url = baseUrl + "/api/json?tree=mode";
        int code = httpGet(url);
        return code == 200;
    }

    public List<RemoteJobInfo> listJobs(String folderPath) throws IOException {
        String apiUrl;
        if (folderPath == null || folderPath.isEmpty() || "/".equals(folderPath)) {
            apiUrl = baseUrl + "/api/json?tree=jobs[name,url,color,jobs[name,url,color,jobs[name,url,color,jobs[name,url,color]]]";
        } else {
            String encodedPath = encodeFolderPath(folderPath);
            apiUrl = baseUrl + "/job/" + encodedPath + "/api/json?tree=jobs[name,url,color,jobs[name,url,color,jobs[name,url,color,jobs[name,url,color]]]";
        }

        String response = httpGetAsString(apiUrl);
        return parseJobsFromJson(response, folderPath != null ? folderPath : "");
    }

    public String getJobConfig(String fullJobPath) throws IOException {
        String encodedPath = encodeFolderPath(fullJobPath);
        String apiUrl = baseUrl + "/job/" + encodedPath + "/config.xml";
        return httpGetAsString(apiUrl);
    }

    private int httpGet(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            return conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private String httpGetAsString(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Authorization", "Basic " + encodedAuth);

            int responseCode = conn.getResponseCode();
            InputStream is;
            if (responseCode >= 200 && responseCode < 300) {
                is = conn.getInputStream();
            } else {
                is = conn.getErrorStream();
            }

            if (is == null) {
                throw new IOException("Empty response with HTTP code: " + responseCode);
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
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

                boolean isFolder = job.has("jobs");
                result.add(new RemoteJobInfo(name, fullDisplayName, isFolder, color));

                if (isFolder) {
                    net.sf.json.JSONArray subJobs = job.optJSONArray("jobs");
                    if (subJobs != null) {
                        for (int j = 0; j < subJobs.size(); j++) {
                            net.sf.json.JSONObject subJob = subJobs.getJSONObject(j);
                            String subName = subJob.optString("name", "");
                            String subFull = fullDisplayName + "/" + subName;
                            boolean subIsFolder = subJob.has("jobs");
                            result.add(new RemoteJobInfo(subName, subFull, subIsFolder, subJob.optString("color", "")));

                            if (subIsFolder) {
                                net.sf.json.JSONArray subSubJobs = subJob.optJSONArray("jobs");
                                if (subSubJobs != null) {
                                    for (int k = 0; k < subSubJobs.size(); k++) {
                                        net.sf.json.JSONObject subSubJob = subSubJobs.getJSONObject(k);
                                        String subSubName = subSubJob.optString("name", "");
                                        String subSubFull = subFull + "/" + subSubName;
                                        result.add(new RemoteJobInfo(subSubName, subSubFull, subSubJob.has("jobs"), subSubJob.optString("color", "")));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse jobs JSON", e);
        }
        return result;
    }
}
