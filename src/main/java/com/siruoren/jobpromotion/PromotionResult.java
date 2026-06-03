package com.siruoren.jobpromotion;

import edu.umd.cs.findbugs.annotations.NonNull;

public class PromotionResult {

    public enum Status {
        SUCCESS, FAILURE, SKIPPED
    }

    private final String jobFullPath;
    private final Status status;
    private final String message;

    private PromotionResult(@NonNull String jobFullPath, @NonNull Status status, String message) {
        this.jobFullPath = jobFullPath;
        this.status = status;
        this.message = message;
    }

    public static PromotionResult success(@NonNull String jobFullPath, String message) {
        return new PromotionResult(jobFullPath, Status.SUCCESS, message);
    }

    public static PromotionResult failure(@NonNull String jobFullPath, String message) {
        return new PromotionResult(jobFullPath, Status.FAILURE, message);
    }

    public static PromotionResult skipped(@NonNull String jobFullPath, String message) {
        return new PromotionResult(jobFullPath, Status.SKIPPED, message);
    }

    public String getJobFullPath() {
        return jobFullPath;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isFailure() {
        return status == Status.FAILURE;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }
}
