(function () {
    "use strict";

    function safeParseJson(text) {
        if (typeof text !== "string") {
            return { success: false, error: "Invalid response type" };
        }
        var trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JSON.parse(trimmed);
            } catch (e) {
                return { success: false, error: "JSON parse error: " + e.message };
            }
        }
        if (trimmed.startsWith("<") || trimmed.startsWith("<!")) {
            return { success: false, error: "Server returned HTML instead of JSON. Session may have expired." };
        }
        return { success: false, error: "Unexpected response format: " + trimmed.substring(0, 200) };
    }

    function getMessage(key) {
        var element = document.getElementById("msg-" + key);
        if (element) {
            return element.textContent || element.innerText || key;
        }
        return key;
    }

    function isFolderPage() {
        var input = document.getElementById("folderPathInput");
        return input && input.readOnly;
    }

    function isRootPage() {
        return !isFolderPage();
    }

    function getCrumbHeader() {
        var meta = document.querySelector('meta[name="crumbHeader"]');
        if (meta) return meta.getAttribute("content");
        if (typeof crumb !== "undefined" && crumb.headerName) return crumb.headerName;
        return "Jenkins-Crumb";
    }

    function getCrumbValue() {
        var meta = document.querySelector('meta[name="crumb"]');
        if (meta) return meta.getAttribute("content");
        if (typeof crumb !== "undefined" && crumb.value) return crumb.value;
        return "";
    }

    function buildHeaders() {
        var headers = {};
        var crumbHeader = getCrumbHeader();
        var crumbValue = getCrumbValue();
        if (crumbHeader && crumbValue) {
            headers[crumbHeader] = crumbValue;
        }
        return headers;
    }

    function getActionUrl() {
        var path = window.location.pathname;
        var idx = path.indexOf("/job-promotion");
        if (idx >= 0) {
            return path.substring(0, idx + "/job-promotion".length);
        }
        return path + "/job-promotion";
    }

    var actionUrl = getActionUrl();

    var loadJobsBtn = document.getElementById("loadJobsBtn");
    var promoteBtn = document.getElementById("promoteBtn");
    var selectAllBtn = document.getElementById("selectAllBtn");
    var deselectAllBtn = document.getElementById("deselectAllBtn");
    var auditLogBtn = document.getElementById("auditLogBtn");
    var jobListSection = document.getElementById("jobListSection");
    var promotionOptions = document.getElementById("promotionOptions");
    var jobTableBody = document.getElementById("jobTableBody");
    var headerCheckbox = document.getElementById("headerCheckbox");
    var sourceInstanceSelect = document.getElementById("sourceInstanceSelect");

    var remoteJobs = [];
    var auditLogPage = 1;
    var auditLogPageSize = 20;

    // Load source instances on page load
    function loadInstances() {
        var headers = buildHeaders();
        fetch(actionUrl + "/getInstances", {
            method: "POST",
            headers: headers,
            credentials: "same-origin",
        })
            .then(function (response) { return response.text(); })
            .then(function (text) {
                var result = safeParseJson(text);
                if (result.success && Array.isArray(result.data)) {
                    if (sourceInstanceSelect) {
                        sourceInstanceSelect.innerHTML = '<option value="">' + getMessage("selectInstance") + '</option>';
                        result.data.forEach(function (inst) {
                            var option = document.createElement("option");
                            option.value = inst.name || inst.url;
                            option.textContent = inst.name ? (inst.name + " (" + inst.url + ")") : inst.url;
                            sourceInstanceSelect.appendChild(option);
                        });
                    }
                }
            })
            .catch(function (err) {
                // Silently fail - instances may not be configured yet
            });
    }

    loadInstances();

    if (loadJobsBtn) {
        loadJobsBtn.addEventListener("click", function () {
            var sourceInstance = sourceInstanceSelect ? sourceInstanceSelect.value : "";

            loadJobsBtn.disabled = true;
            var originalText = loadJobsBtn.getAttribute("data-original-text") || loadJobsBtn.textContent;
            loadJobsBtn.textContent = getMessage("loading") || "Loading...";

            var formData = new FormData();
            if (!isFolderPage()) {
                var folderPath = document.getElementById("folderPathInput").value.trim();
                formData.append("folderPath", folderPath);
            }
            if (sourceInstance) {
                formData.append("sourceInstance", sourceInstance);
            }

            var headers = buildHeaders();

            fetch(actionUrl + "/listRemoteJobs", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success && Array.isArray(result.data)) {
                        remoteJobs = result.data;
                        renderJobList(remoteJobs);
                        jobListSection.style.display = "block";
                        promotionOptions.style.display = remoteJobs.length > 0 ? "block" : "none";
                    } else {
                        alert(result.error || "Failed to load jobs");
                    }
                })
                .catch(function (err) {
                    alert("Network error: " + err.message);
                })
                .finally(function () {
                    loadJobsBtn.disabled = false;
                    loadJobsBtn.textContent = originalText;
                });
        });
    }

    function renderJobList(jobs) {
        jobTableBody.innerHTML = "";
        var folderLabel = getMessage("folder") || "Folder";
        var jobLabel = getMessage("job") || "Job";

        jobs.forEach(function (job, index) {
            var tr = document.createElement("tr");
            var typeLabel = job.folder ? folderLabel : jobLabel;

            tr.innerHTML =
                '<td><input type="checkbox" class="job-checkbox" data-index="' +
                index +
                '" data-fullpath="' +
                escapeHtml(job.fullDisplayName) +
                '" data-folder="' +
                job.folder +
                '"/></td>' +
                "<td>" +
                escapeHtml(job.name) +
                "</td>" +
                "<td>" +
                escapeHtml(job.fullDisplayName) +
                "</td>" +
                "<td>" +
                typeLabel +
                "</td>";
            jobTableBody.appendChild(tr);
        });

        var checkboxes = document.querySelectorAll(".job-checkbox");
        checkboxes.forEach(function (cb) {
            cb.addEventListener("change", function () {
                updateHeaderCheckbox();
            });
        });
    }

    function updateHeaderCheckbox() {
        var checkboxes = document.querySelectorAll(".job-checkbox");
        var checked = document.querySelectorAll(".job-checkbox:checked");
        if (checkboxes.length === 0) return;
        headerCheckbox.checked = checked.length === checkboxes.length;
        headerCheckbox.indeterminate = checked.length > 0 && checked.length < checkboxes.length;
    }

    if (headerCheckbox) {
        headerCheckbox.addEventListener("change", function () {
            var checkboxes = document.querySelectorAll(".job-checkbox");
            checkboxes.forEach(function (cb) {
                cb.checked = headerCheckbox.checked;
            });
        });
    }

    if (selectAllBtn) {
        selectAllBtn.addEventListener("click", function () {
            var checkboxes = document.querySelectorAll(".job-checkbox");
            checkboxes.forEach(function (cb) { cb.checked = true; });
            if (headerCheckbox) headerCheckbox.checked = true;
        });
    }

    if (deselectAllBtn) {
        deselectAllBtn.addEventListener("click", function () {
            var checkboxes = document.querySelectorAll(".job-checkbox");
            checkboxes.forEach(function (cb) { cb.checked = false; });
            if (headerCheckbox) headerCheckbox.checked = false;
        });
    }

    if (promoteBtn) {
        promoteBtn.addEventListener("click", function () {
            var checked = document.querySelectorAll(".job-checkbox:checked");
            if (checked.length === 0) {
                alert(getMessage("noJobsSelected") || "Please select at least one job to promote");
                return;
            }

            var selectedJobs = [];
            checked.forEach(function (cb) {
                var fullPath = cb.getAttribute("data-fullpath");
                var isFolder = cb.getAttribute("data-folder") === "true";
                selectedJobs.push(fullPath + "|" + isFolder);
            });

            var forceUpdate = document.querySelector('input[name="updateMode"]:checked').value === "force";

            showConfirmDialog(selectedJobs, forceUpdate);
        });
    }

    function showConfirmDialog(selectedJobs, forceUpdate) {
        var overlay = document.createElement("div");
        overlay.className = "jp-dialog-overlay";

        var dialog = document.createElement("div");
        dialog.className = "jp-dialog";

        var jobListHtml = "<ul class='jp-job-list'>";
        selectedJobs.forEach(function (job) {
            var displayName = job.split("|")[0];
            var isFolder = job.split("|")[1] === "true";
            var typeIcon = isFolder ? "\uD83D\uDCC1 " : "\uD83D\uDCC4 ";
            jobListHtml += "<li>" + typeIcon + escapeHtml(displayName) + "</li>";
        });
        jobListHtml += "</ul>";

        dialog.innerHTML =
            "<h2>" + getMessage("confirm-title") + "</h2>" +
            "<p>" + getMessage("confirm-desc") + "</p>" +
            jobListHtml +
            "<p>" + getMessage("mode-label") + " " +
            (forceUpdate ? getMessage("force-update") : getMessage("normal-update")) +
            "</p>" +
            "<div class='jp-dialog-actions'>" +
            "<button class='jenkins-button jenkins-button--primary' id='confirmPromoteBtn'>" + getMessage("confirm-btn") + "</button>" +
            "<button class='jenkins-button' id='cancelPromoteBtn'>" + getMessage("cancel-btn") + "</button>" +
            "</div>";

        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        document.getElementById("cancelPromoteBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
        });

        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) document.body.removeChild(overlay);
        });

        document.getElementById("confirmPromoteBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
            executePromotion(selectedJobs, forceUpdate);
        });
    }

    function executePromotion(selectedJobs, forceUpdate) {
        promoteBtn.disabled = true;
        var originalText = promoteBtn.getAttribute("data-original-text") || promoteBtn.textContent;
        promoteBtn.textContent = getMessage("loading") || "Promoting...";

        var sourceInstance = sourceInstanceSelect ? sourceInstanceSelect.value : "";

        var formData = new FormData();
        formData.append("jobs", selectedJobs.join(","));
        formData.append("forceUpdate", forceUpdate ? "true" : "false");
        if (sourceInstance) {
            formData.append("sourceInstance", sourceInstance);
        }

        var headers = buildHeaders();

        fetch(actionUrl + "/promoteJobs", {
            method: "POST",
            headers: headers,
            body: formData,
            credentials: "same-origin",
        })
            .then(function (response) { return response.text(); })
            .then(function (text) {
                var result = safeParseJson(text);
                if (result.success && Array.isArray(result.data)) {
                    showResultDialog(result.data);
                } else {
                    showResultDialog([
                        { jobFullPath: "Error", status: "FAILURE", message: result.error || "Unknown error" },
                    ]);
                }
            })
            .catch(function (err) {
                showResultDialog([
                    { jobFullPath: "Network Error", status: "FAILURE", message: err.message },
                ]);
            })
            .finally(function () {
                promoteBtn.disabled = false;
                promoteBtn.textContent = originalText;
            });
    }

    function showResultDialog(results) {
        var overlay = document.createElement("div");
        overlay.className = "jp-dialog-overlay";

        var dialog = document.createElement("div");
        dialog.className = "jp-dialog jp-dialog-result";

        var resultHtml = "<table class='jenkins-table'><thead><tr><th>" + getMessage("result-job") + "</th><th>" + getMessage("result-status") + "</th><th>" + getMessage("result-message") + "</th></tr></thead><tbody>";
        results.forEach(function (r) {
            var statusClass = "jp-status-" + r.status.toLowerCase();
            var statusText = r.status;
            if (r.status === "SUCCESS") statusText = getMessage("success");
            else if (r.status === "SKIPPED") statusText = getMessage("skipped");
            else if (r.status === "FAILURE") statusText = getMessage("failure");
            resultHtml +=
                "<tr>" +
                "<td>" + escapeHtml(r.jobFullPath) + "</td>" +
                "<td class='" + statusClass + "'>" + statusText + "</td>" +
                "<td>" + escapeHtml(r.message || "") + "</td>" +
                "</tr>";
        });
        resultHtml += "</tbody></table>";

        dialog.innerHTML =
            "<h2>" + getMessage("result-title") + "</h2>" +
            resultHtml +
            "<div class='jp-dialog-actions'>" +
            "<button class='jenkins-button jenkins-button--primary' id='closeResultBtn'>" + getMessage("close-btn") + "</button>" +
            "</div>";

        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        document.getElementById("closeResultBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
        });

        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) document.body.removeChild(overlay);
        });
    }

    // Audit Log functionality (root page only)
    if (auditLogBtn) {
        auditLogBtn.addEventListener("click", function () {
            auditLogPage = 1;
            showAuditLogDialog();
        });
    }

    function showAuditLogDialog() {
        var overlay = document.createElement("div");
        overlay.className = "jp-dialog-overlay";

        var dialog = document.createElement("div");
        dialog.className = "jp-dialog jp-dialog-audit";

        dialog.innerHTML =
            "<h2>" + getMessage("auditLog-title") + "</h2>" +
            "<div class='jp-audit-retention'>" +
            "<label>" + getMessage("auditLog-retention") + " <input type='number' id='auditRetentionDays' min='1' value='' style='width:60px;'/> " + getMessage("auditLog-days") + "</label>" +
            "<button class='jenkins-button jenkins-button--primary' id='saveRetentionBtn' style='margin-left:8px;'>" + getMessage("auditLog-retentionSave") + "</button>" +
            "</div>" +
            "<div id='auditLogContent' style='margin-top:12px;'></div>" +
            "<div class='jp-dialog-actions'>" +
            "<button class='jenkins-button' id='auditLogPrevBtn'>" + getMessage("auditLog-prev") + "</button>" +
            "<span id='auditLogPageInfo' style='margin:0 8px;'></span>" +
            "<button class='jenkins-button' id='auditLogNextBtn'>" + getMessage("auditLog-next") + "</button>" +
            "<button class='jenkins-button jenkins-button--primary' id='closeAuditBtn'>" + getMessage("close-btn") + "</button>" +
            "</div>";

        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        document.getElementById("closeAuditBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
        });

        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) document.body.removeChild(overlay);
        });

        document.getElementById("auditLogPrevBtn").addEventListener("click", function () {
            if (auditLogPage > 1) {
                auditLogPage--;
                loadAuditLogs();
            }
        });

        document.getElementById("auditLogNextBtn").addEventListener("click", function () {
            auditLogPage++;
            loadAuditLogs();
        });

        document.getElementById("saveRetentionBtn").addEventListener("click", function () {
            var days = document.getElementById("auditRetentionDays").value;
            if (!days || parseInt(days) < 1) {
                alert("Invalid retention days");
                return;
            }
            var formData = new FormData();
            formData.append("retentionDays", days);
            var headers = buildHeaders();
            fetch(actionUrl + "/updateAuditLogRetention", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success) {
                        alert("Retention days updated to " + days);
                    } else {
                        alert(result.error || "Failed to update retention days");
                    }
                })
                .catch(function (err) {
                    alert("Error: " + err.message);
                });
        });

        loadAuditLogs();
    }

    function loadAuditLogs() {
        var contentDiv = document.getElementById("auditLogContent");
        if (!contentDiv) return;

        contentDiv.innerHTML = "<p>" + (getMessage("loading") || "Loading...") + "</p>";

        var formData = new FormData();
        formData.append("page", auditLogPage.toString());
        formData.append("pageSize", auditLogPageSize.toString());
        var headers = buildHeaders();

        fetch(actionUrl + "/getAuditLogs", {
            method: "POST",
            headers: headers,
            body: formData,
            credentials: "same-origin",
        })
            .then(function (response) { return response.text(); })
            .then(function (text) {
                var result = safeParseJson(text);
                if (result.success && result.data) {
                    var data = result.data;
                    var logs = data.logs || [];
                    var total = data.total || 0;
                    var page = data.page || 1;
                    var pageSize = data.pageSize || 20;
                    var totalPages = Math.ceil(total / pageSize) || 1;

                    // Update retention days input
                    var retentionInput = document.getElementById("auditRetentionDays");
                    if (retentionInput && !retentionInput.value) {
                        // Try to get from global config - just leave default for now
                    }

                    var pageInfo = document.getElementById("auditLogPageInfo");
                    if (pageInfo) {
                        pageInfo.textContent = page + " / " + totalPages;
                    }

                    if (logs.length === 0) {
                        contentDiv.innerHTML = "<p>" + getMessage("auditLog-noLogs") + "</p>";
                        return;
                    }

                    var html = "<table class='jenkins-table'><thead><tr>" +
                        "<th>" + getMessage("auditLog-time") + "</th>" +
                        "<th>" + getMessage("auditLog-user") + "</th>" +
                        "<th>" + getMessage("auditLog-instance") + "</th>" +
                        "<th>" + getMessage("auditLog-jobs") + "</th>" +
                        "<th>" + getMessage("auditLog-mode") + "</th>" +
                        "<th>" + getMessage("auditLog-result") + "</th>" +
                        "</tr></thead><tbody>";

                    logs.forEach(function (log) {
                        var mode = log.forceUpdate ? getMessage("force-update") : getMessage("normal-update");
                        var resultText = getMessage("success") + ":" + log.successCount + " " +
                            getMessage("failure") + ":" + log.failureCount + " " +
                            getMessage("skipped") + ":" + log.skippedCount;
                        html += "<tr>" +
                            "<td>" + escapeHtml(log.formattedTimestamp || "") + "</td>" +
                            "<td>" + escapeHtml(log.username || "") + "</td>" +
                            "<td>" + escapeHtml(log.sourceInstance || "") + "</td>" +
                            "<td title='" + escapeHtml((log.jobPaths || []).join(", ")) + "'>" + escapeHtml(log.jobPathsSummary || "") + "</td>" +
                            "<td>" + mode + "</td>" +
                            "<td>" + resultText + "</td>" +
                            "</tr>";
                    });

                    html += "</tbody></table>";
                    contentDiv.innerHTML = html;
                } else {
                    contentDiv.innerHTML = "<p>" + (result.error || "Failed to load audit logs") + "</p>";
                }
            })
            .catch(function (err) {
                contentDiv.innerHTML = "<p>Error: " + escapeHtml(err.message) + "</p>";
            });
    }

    function escapeHtml(str) {
        if (!str) return "";
        return str
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#039;");
    }
})();
