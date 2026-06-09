package com.siruoren.jobpromotion.util;

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

import com.siruoren.jobpromotion.PromotionResult;
import com.siruoren.jobpromotion.RemoteJobInfo;

/**
 * Utility class for building JSON HTTP responses.
 * Consolidates JsonResponse and JsonResponseerror into a single utility.
 */
public class JsonResponseUtil {

    private JsonResponseUtil() {
    }

    /**
     * Create a success response with arbitrary data.
     */
    public static HttpResponse success(@NonNull Object data) {
        return new SuccessResponse(data);
    }

    /**
     * Create a success response from a list of RemoteJobInfo.
     */
    public static HttpResponse success(@NonNull List<RemoteJobInfo> jobs) {
        JSONArray arr = new JSONArray();
        for (RemoteJobInfo job : jobs) {
            JSONObject obj = new JSONObject();
            obj.put("name", job.getName());
            obj.put("fullDisplayName", job.getFullDisplayName());
            obj.put("folder", job.isFolder());
            obj.put("color", job.getColor() != null ? job.getColor() : "");
            arr.add(obj);
        }
        return new SuccessResponse(arr);
    }

    /**
     * Create a success response from a map of PromotionResult.
     */
    public static HttpResponse success(@NonNull Map<String, PromotionResult> results) {
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, PromotionResult> entry : results.entrySet()) {
            JSONObject resultObj = new JSONObject();
            resultObj.put("jobFullPath", entry.getValue().getJobFullPath());
            resultObj.put("status", entry.getValue().getStatus().name());
            resultObj.put("message", entry.getValue().getMessage() != null ? entry.getValue().getMessage() : "");
            arr.add(resultObj);
        }
        return new SuccessResponse(arr);
    }

    /**
     * Create an error response.
     */
    public static HttpResponse error(@NonNull String errorMessage) {
        return new ErrorResponse(errorMessage);
    }

    private static class SuccessResponse implements HttpResponse {
        private final Object data;

        SuccessResponse(@NonNull Object data) {
            this.data = data;
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, @NonNull Object node) throws IOException {
            rsp.setContentType("application/json;charset=UTF-8");
            rsp.setStatus(200);
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("data", data);
            try (PrintWriter w = rsp.getWriter()) {
                w.write(response.toString());
            }
        }
    }

    private static class ErrorResponse implements HttpResponse {
        private final String errorMessage;

        ErrorResponse(@NonNull String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public void generateResponse(StaplerRequest2 req, StaplerResponse2 rsp, @NonNull Object node) throws IOException {
            rsp.setContentType("application/json;charset=UTF-8");
            rsp.setStatus(200);
            JSONObject response = new JSONObject();
            response.put("success", false);
            response.put("error", errorMessage);
            try (PrintWriter w = rsp.getWriter()) {
                w.write(response.toString());
            }
        }
    }
}
