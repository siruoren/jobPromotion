package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;

import java.io.IOException;
import java.io.PrintWriter;

public class JsonResponseerror implements HttpResponse {

    private final String errorMessage;

    public JsonResponseerror(@NonNull String errorMessage) {
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
