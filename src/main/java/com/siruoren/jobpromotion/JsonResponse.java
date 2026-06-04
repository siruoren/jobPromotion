package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

public class JsonResponse implements HttpResponse {

    private final Object data;
    private final boolean success;
    private final String errorMessage;

    public JsonResponse(@NonNull Object data) {
        this.data = data;
        this.success = true;
        this.errorMessage = null;
    }

    public JsonResponse(@NonNull List<RemoteJobInfo> jobs) {
        JSONArray arr = new JSONArray();
        for (RemoteJobInfo job : jobs) {
            JSONObject obj = new JSONObject();
            obj.put("name", job.getName());
            obj.put("fullDisplayName", job.getFullDisplayName());
            obj.put("folder", job.isFolder());
            obj.put("color", job.getColor() != null ? job.getColor() : "");
            arr.add(obj);
        }
        this.data = arr;
        this.success = true;
        this.errorMessage = null;
    }

    public JsonResponse(@NonNull Map<String, PromotionResult> results) {
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, PromotionResult> entry : results.entrySet()) {
            JSONObject resultObj = new JSONObject();
            resultObj.put("jobFullPath", entry.getValue().getJobFullPath());
            resultObj.put("status", entry.getValue().getStatus().name());
            resultObj.put("message", entry.getValue().getMessage() != null ? entry.getValue().getMessage() : "");
            arr.add(resultObj);
        }
        this.data = arr;
        this.success = true;
        this.errorMessage = null;
    }

    @Override
    public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, @NonNull Object node) throws IOException {
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.setStatus(200);
        JSONObject response = new JSONObject();
        response.put("success", success);
        if (success) {
            response.put("data", data);
        } else {
            response.put("error", errorMessage);
        }
        try (PrintWriter w = rsp.getWriter()) {
            w.write(response.toString());
        }
    }
}
