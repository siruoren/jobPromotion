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

    function escapeHtml(str) {
        if (!str) return "";
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    var actionUrl = getActionUrl();

    // ==================== DOM Elements ====================
    // Delivery column
    var loadLocalJobsBtn = document.getElementById("loadLocalJobsBtn");
    var jobListSection = document.getElementById("jobListSection");
    var jobTableBody = document.getElementById("jobTableBody");
    var headerCheckbox = document.getElementById("headerCheckbox");
    var selectAllBtn = document.getElementById("selectAllBtn");
    var deselectAllBtn = document.getElementById("deselectAllBtn");
    var deliverBtn = document.getElementById("deliverBtn");
    var deliveryListSection = document.getElementById("deliveryListSection");
    var deliveryTableBody = document.getElementById("deliveryTableBody");
    var deliveryHeaderCheckbox = document.getElementById("deliveryHeaderCheckbox");
    var loadDeliveryBtn = document.getElementById("loadDeliveryBtn");
    var cancelDeliveryBtn = document.getElementById("cancelDeliveryBtn");
    var deliveryStatusFilter = document.getElementById("deliveryStatusFilter");

    // Promotion column
    var sourceInstanceSelect = document.getElementById("sourceInstanceSelect");
    var loadJobsBtn = document.getElementById("loadJobsBtn");
    var remoteJobListSection = document.getElementById("remoteJobListSection");
    var remoteJobTableBody = document.getElementById("remoteJobTableBody");
    var remoteHeaderCheckbox = document.getElementById("remoteHeaderCheckbox");
    var selectAllRemoteBtn = document.getElementById("selectAllRemoteBtn");
    var deselectAllRemoteBtn = document.getElementById("deselectAllRemoteBtn");
    var promotionOptions = document.getElementById("promotionOptions");
    var promoteBtn = document.getElementById("promoteBtn");
    var auditLogBtn = document.getElementById("auditLogBtn");

    var localJobs = [];
    var remoteJobs = [];
    var deliveryItems = [];
    var auditLogPage = 1;
    var auditLogPageSize = 20;

    // ==================== Load Instances ====================
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
            .catch(function () {});
    }

    loadInstances();

    // Auto-load delivery list on page load
    if (loadDeliveryBtn) {
        setTimeout(function () { loadDeliveryBtn.click(); }, 300);
    }

    // When source instance changes, auto-fetch delivery list from source Jenkins
    if (sourceInstanceSelect) {
        sourceInstanceSelect.addEventListener("change", function () {
            if (sourceInstanceSelect.value) {
                fetchSourceDeliveryList();
            }
        });
    }

    // ==================== Load Local Jobs (for delivery) ====================
    if (loadLocalJobsBtn) {
        loadLocalJobsBtn.addEventListener("click", function () {
            loadLocalJobsBtn.disabled = true;
            var originalText = loadLocalJobsBtn.getAttribute("data-original-text") || loadLocalJobsBtn.textContent;
            loadLocalJobsBtn.textContent = getMessage("loading") || "Loading...";

            var formData = new FormData();
            // Load local jobs from current folder
            var folderPathInput = document.getElementById("folderPathInput");
            var folderPath = folderPathInput ? folderPathInput.value.trim() : "";
            if (folderPath) {
                formData.append("folderPath", folderPath);
            }

            var headers = buildHeaders();

            fetch(actionUrl + "/listLocalJobs", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success && Array.isArray(result.data)) {
                        localJobs = result.data;
                        renderLocalJobList(localJobs);
                        jobListSection.style.display = "block";
                    } else {
                        alert(result.error || "Failed to load local jobs");
                    }
                })
                .catch(function (err) {
                    alert("Network error: " + err.message);
                })
                .finally(function () {
                    loadLocalJobsBtn.disabled = false;
                    loadLocalJobsBtn.textContent = originalText;
                });
        });
    }

    // ==================== Render Local Job List ====================
    function renderLocalJobList(jobs) {
        jobTableBody.innerHTML = "";
        var folderLabel = getMessage("folder") || "Folder";
        var jobLabel = getMessage("job") || "Job";

        jobs.forEach(function (job, index) {
            var tr = document.createElement("tr");
            var typeLabel = job.folder ? folderLabel : jobLabel;
            tr.innerHTML =
                '<td><input type="checkbox" class="job-checkbox" data-index="' + index +
                '" data-fullpath="' + escapeHtml(job.fullDisplayName || job.name) +
                '" data-folder="' + (job.folder || false) + '"/></td>' +
                "<td>" + escapeHtml(job.name) + "</td>" +
                "<td>" + escapeHtml(job.fullDisplayName || job.name) + "</td>" +
                "<td>" + typeLabel + "</td>";
            jobTableBody.appendChild(tr);
        });

        document.querySelectorAll(".job-checkbox").forEach(function (cb) {
            cb.addEventListener("change", function () { updateHeaderCheckbox(); });
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
            document.querySelectorAll(".job-checkbox").forEach(function (cb) { cb.checked = headerCheckbox.checked; });
        });
    }

    if (selectAllBtn) {
        selectAllBtn.addEventListener("click", function () {
            document.querySelectorAll(".job-checkbox").forEach(function (cb) { cb.checked = true; });
            if (headerCheckbox) headerCheckbox.checked = true;
        });
    }

    if (deselectAllBtn) {
        deselectAllBtn.addEventListener("click", function () {
            document.querySelectorAll(".job-checkbox").forEach(function (cb) { cb.checked = false; });
            if (headerCheckbox) headerCheckbox.checked = false;
        });
    }

    // ==================== Deliver Jobs ====================
    if (deliverBtn) {
        deliverBtn.addEventListener("click", function () {
            var checked = document.querySelectorAll(".job-checkbox:checked");
            if (checked.length === 0) {
                alert(getMessage("noJobsSelected") || "Please select at least one job");
                return;
            }

            var selectedJobs = [];
            checked.forEach(function (cb) {
                var fullPath = cb.getAttribute("data-fullpath");
                var isFolder = cb.getAttribute("data-folder") === "true";
                selectedJobs.push(fullPath + "|" + isFolder);
            });

            deliverBtn.disabled = true;
            var originalText = deliverBtn.getAttribute("data-original-text") || deliverBtn.textContent;
            deliverBtn.textContent = getMessage("loading") || "Delivering...";

            var formData = new FormData();
            formData.append("jobs", selectedJobs.join(","));

            var headers = buildHeaders();

            fetch(actionUrl + "/deliverJobs", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success && result.data) {
                        var deliveredCount = result.data.deliveredCount || 0;
                        alert(getMessage("deliverSuccess") + ": " + deliveredCount);
                        if (loadDeliveryBtn) loadDeliveryBtn.click();
                    } else {
                        alert(result.error || getMessage("deliverFailed"));
                    }
                })
                .catch(function (err) {
                    alert("Network error: " + err.message);
                })
                .finally(function () {
                    deliverBtn.disabled = false;
                    deliverBtn.textContent = originalText;
                });
        });
    }

    // ==================== Load Delivery List ====================
    if (loadDeliveryBtn) {
        loadDeliveryBtn.addEventListener("click", function () {
            var headers = buildHeaders();
            var formData = new FormData();
            if (deliveryStatusFilter) {
                formData.append("status", deliveryStatusFilter.value);
            }

            fetch(actionUrl + "/getDeliveryList", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success && result.data) {
                        deliveryItems = result.data.items || [];
                        renderDeliveryList(deliveryItems);
                        if (deliveryListSection) {
                            deliveryListSection.style.display = "block";
                        }
                    }
                })
                .catch(function () {});
        });
    }

    // ==================== Render Delivery List ====================
    function renderDeliveryList(items) {
        if (!deliveryTableBody) return;
        deliveryTableBody.innerHTML = "";

        if (items.length === 0) {
            deliveryTableBody.innerHTML = '<tr><td colspan="7" style="text-align:center;">' + (getMessage("noDeliveryItems") || "No delivery items") + '</td></tr>';
            return;
        }

        var folderLabel = getMessage("folder") || "Folder";
        var jobLabel = getMessage("job") || "Job";

        items.forEach(function (item) {
            var tr = document.createElement("tr");
            var typeLabel = item.folder ? folderLabel : jobLabel;
            var statusText = "";
            var statusClass = "";

            if (item.status === "DELIVERED") {
                statusText = getMessage("statusDelivered") || "Delivered";
                statusClass = "jp-status-delivered";
            } else if (item.status === "PROMOTED") {
                statusText = getMessage("statusPromoted") || "Promoted";
                statusClass = "jp-status-promoted";
            } else if (item.status === "CANCELLED") {
                statusText = getMessage("statusCancelled") || "Cancelled";
                statusClass = "jp-status-cancelled";
            } else if (item.status === "EXPIRED") {
                statusText = getMessage("statusExpired") || "Expired";
                statusClass = "jp-status-expired";
            }

            var checkboxHtml = item.status === "DELIVERED"
                ? '<input type="checkbox" class="delivery-checkbox" data-id="' + escapeHtml(item.id) + '"/>'
                : '';

            tr.innerHTML =
                "<td>" + checkboxHtml + "</td>" +
                "<td>" + escapeHtml(item.jobName) + "</td>" +
                "<td>" + escapeHtml(item.jobFullPath) + "</td>" +
                "<td>" + typeLabel + "</td>" +
                "<td class='" + statusClass + "'>" + statusText + "</td>" +
                "<td>" + escapeHtml(item.formattedDeliveredAt) + "</td>" +
                (item.status === "PROMOTED" ? "<td>" + escapeHtml(item.formattedPromotedAt) + "</td>" : "<td>-</td>");

            deliveryTableBody.appendChild(tr);
        });

        // Delivery header checkbox logic
        if (deliveryHeaderCheckbox) {
            deliveryHeaderCheckbox.checked = false;
            deliveryHeaderCheckbox.indeterminate = false;
            deliveryHeaderCheckbox.onchange = function () {
                document.querySelectorAll(".delivery-checkbox").forEach(function (cb) {
                    cb.checked = deliveryHeaderCheckbox.checked;
                });
            };
        }
        document.querySelectorAll(".delivery-checkbox").forEach(function (cb) {
            cb.addEventListener("change", function () {
                if (!deliveryHeaderCheckbox) return;
                var all = document.querySelectorAll(".delivery-checkbox");
                var checked = document.querySelectorAll(".delivery-checkbox:checked");
                deliveryHeaderCheckbox.checked = all.length > 0 && checked.length === all.length;
                deliveryHeaderCheckbox.indeterminate = checked.length > 0 && checked.length < all.length;
            });
        });
    }

    // ==================== Cancel Delivery ====================
    if (cancelDeliveryBtn) {
        cancelDeliveryBtn.addEventListener("click", function () {
            var checked = document.querySelectorAll(".delivery-checkbox:checked");
            if (checked.length === 0) {
                alert(getMessage("noJobsSelected") || "Please select items to cancel");
                return;
            }

            var ids = [];
            checked.forEach(function (cb) { ids.push(cb.getAttribute("data-id")); });

            if (!confirm(getMessage("confirmCancelDelivery") || "Cancel delivery for selected items?")) return;

            cancelDeliveryBtn.disabled = true;

            var formData = new FormData();
            formData.append("ids", ids.join(","));

            var headers = buildHeaders();

            fetch(actionUrl + "/cancelDelivery", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) { return response.text(); })
                .then(function (text) {
                    var result = safeParseJson(text);
                    if (result.success) {
                        alert(getMessage("cancelSuccess") + ": " + (result.data.cancelledCount || 0));
                        if (loadDeliveryBtn) loadDeliveryBtn.click();
                    } else {
                        alert(result.error || "Failed");
                    }
                })
                .catch(function (err) {
                    alert("Network error: " + err.message);
                })
                .finally(function () {
                    cancelDeliveryBtn.disabled = false;
                });
        });
    }

    // ==================== Fetch Source Delivery List (Remote Jenkins) ====================
    function fetchSourceDeliveryList() {
        var sourceInstance = sourceInstanceSelect ? sourceInstanceSelect.value : "";
        if (!sourceInstance) return;

        var folderPathInput = document.getElementById("folderPathInput");
        var folderPath = folderPathInput ? folderPathInput.value.trim() : "";

        var formData = new FormData();
        formData.append("sourceInstance", sourceInstance);
        formData.append("folderPath", folderPath);

        var headers = buildHeaders();

        fetch(actionUrl + "/fetchSourceDeliveryList", {
            method: "POST",
            headers: headers,
            body: formData,
            credentials: "same-origin",
        })
            .then(function (response) { return response.text(); })
            .then(function (text) {
                var result = safeParseJson(text);
                if (result.success && result.data) {
                    remoteJobs = result.data.items || [];
                    renderRemoteJobList(remoteJobs);
                    if (remoteJobListSection) {
                        remoteJobListSection.style.display = "block";
                    }
                    var hasDelivered = remoteJobs.some(function (item) { return item.status === "DELIVERED"; });
                    if (hasDelivered && promotionOptions) {
                        promotionOptions.style.display = "block";
                    }
                }
            })
            .catch(function () {});
    }

    // ==================== Load Remote Jobs (for promotion) ====================
    if (loadJobsBtn) {
        loadJobsBtn.addEventListener("click", function () {
            var sourceInstance = sourceInstanceSelect ? sourceInstanceSelect.value : "";
            if (!sourceInstance) {
                alert(getMessage("selectInstance") || "Please select a source instance");
                return;
            }

            // Fetch delivery list from source Jenkins
            fetchSourceDeliveryList();
        });
    }

    // ==================== Render Remote Job List (from source delivery list) ====================
    function renderRemoteJobList(items) {
        if (!remoteJobTableBody) return;
        remoteJobTableBody.innerHTML = "";

        if (items.length === 0) {
            remoteJobTableBody.innerHTML = '<tr><td colspan="6" style="text-align:center;">' + (getMessage("noDeliveryItems") || "No items") + '</td></tr>';
            return;
        }

        var folderLabel = getMessage("folder") || "Folder";
        var jobLabel = getMessage("job") || "Job";

        items.forEach(function (item, index) {
            var tr = document.createElement("tr");
            var typeLabel = item.folder ? folderLabel : jobLabel;
            var statusText = "";
            var statusClass = "";

            if (item.status === "DELIVERED") {
                statusText = getMessage("statusDelivered") || "Delivered";
                statusClass = "jp-status-delivered";
            } else if (item.status === "PROMOTED") {
                statusText = getMessage("statusPromoted") || "Promoted";
                statusClass = "jp-status-promoted";
            } else if (item.status === "CANCELLED") {
                statusText = getMessage("statusCancelled") || "Cancelled";
                statusClass = "jp-status-cancelled";
            } else if (item.status === "EXPIRED") {
                statusText = getMessage("statusExpired") || "Expired";
                statusClass = "jp-status-expired";
            }

            var checkboxHtml = item.status === "DELIVERED"
                ? '<input type="checkbox" class="remote-checkbox" data-index="' + index +
                  '" data-fullpath="' + escapeHtml(item.jobFullPath) +
                  '" data-folder="' + item.folder + '"/>'
                : '';

            tr.innerHTML =
                "<td>" + checkboxHtml + "</td>" +
                "<td>" + escapeHtml(item.jobName) + "</td>" +
                "<td>" + escapeHtml(item.jobFullPath) + "</td>" +
                "<td>" + typeLabel + "</td>" +
                "<td class='" + statusClass + "'>" + statusText + "</td>" +
                "<td>" + escapeHtml(item.formattedDeliveredAt || "") + "</td>";

            remoteJobTableBody.appendChild(tr);
        });

        // Remote header checkbox logic
        if (remoteHeaderCheckbox) {
            remoteHeaderCheckbox.checked = false;
            remoteHeaderCheckbox.indeterminate = false;
            remoteHeaderCheckbox.onchange = function () {
                document.querySelectorAll(".remote-checkbox").forEach(function (cb) {
                    cb.checked = remoteHeaderCheckbox.checked;
                });
            };
        }
        document.querySelectorAll(".remote-checkbox").forEach(function (cb) {
            cb.addEventListener("change", function () {
                if (!remoteHeaderCheckbox) return;
                var all = document.querySelectorAll(".remote-checkbox");
                var checked = document.querySelectorAll(".remote-checkbox:checked");
                remoteHeaderCheckbox.checked = all.length > 0 && checked.length === all.length;
                remoteHeaderCheckbox.indeterminate = checked.length > 0 && checked.length < all.length;
            });
        });
    }

    if (selectAllRemoteBtn) {
        selectAllRemoteBtn.addEventListener("click", function () {
            document.querySelectorAll(".remote-checkbox").forEach(function (cb) { cb.checked = true; });
            if (remoteHeaderCheckbox) remoteHeaderCheckbox.checked = true;
        });
    }

    if (deselectAllRemoteBtn) {
        deselectAllRemoteBtn.addEventListener("click", function () {
            document.querySelectorAll(".remote-checkbox").forEach(function (cb) { cb.checked = false; });
            if (remoteHeaderCheckbox) remoteHeaderCheckbox.checked = false;
        });
    }

    // ==================== Promote Jobs ====================
    if (promoteBtn) {
        promoteBtn.addEventListener("click", function () {
            var checked = document.querySelectorAll(".remote-checkbox:checked");
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

            var forceUpdate = document.querySelector('input[name="updateMode"]:checked');
            var isForce = forceUpdate ? forceUpdate.value === "force" : false;
            var sourceInstance = sourceInstanceSelect ? sourceInstanceSelect.value : "";

            showConfirmDialog(selectedJobs, isForce, sourceInstance);
        });
    }

    function showConfirmDialog(selectedJobs, forceUpdate, sourceInstance) {
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
            executePromotion(selectedJobs, forceUpdate, sourceInstance);
        });
    }

    function executePromotion(selectedJobs, forceUpdate, sourceInstance) {
        promoteBtn.disabled = true;
        var originalText = promoteBtn.getAttribute("data-original-text") || promoteBtn.textContent;
        promoteBtn.textContent = getMessage("loading") || "Promoting...";

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
                // Refresh delivery list
                if (loadDeliveryBtn) loadDeliveryBtn.click();
                // Refresh remote job list
                if (sourceInstanceSelect && sourceInstanceSelect.value) {
                    fetchSourceDeliveryList();
                }
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

    // ==================== Audit Log ====================
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
            if (auditLogPage > 1) { auditLogPage--; loadAuditLogs(); }
        });

        document.getElementById("auditLogNextBtn").addEventListener("click", function () {
            auditLogPage++;
            loadAuditLogs();
        });

        document.getElementById("saveRetentionBtn").addEventListener("click", function () {
            var days = document.getElementById("auditRetentionDays").value;
            if (!days || parseInt(days) < 1) { alert("Invalid retention days"); return; }
            var formData = new FormData();
            formData.append("retentionDays", days);
            var headers = buildHeaders();
            fetch(actionUrl + "/updateAuditLogRetention", {
                method: "POST", headers: headers, body: formData, credentials: "same-origin",
            })
                .then(function (r) { return r.text(); })
                .then(function (t) {
                    var result = safeParseJson(t);
                    if (result.success) alert("Retention days updated to " + days);
                    else alert(result.error || "Failed");
                })
                .catch(function (err) { alert("Error: " + err.message); });
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
            method: "POST", headers: headers, body: formData, credentials: "same-origin",
        })
            .then(function (r) { return r.text(); })
            .then(function (text) {
                var result = safeParseJson(text);
                if (result.success && result.data) {
                    var data = result.data;
                    var logs = data.logs || [];
                    var total = data.total || 0;
                    var page = data.page || 1;
                    var pageSize = data.pageSize || 20;
                    var totalPages = Math.ceil(total / pageSize) || 1;

                    var retentionInput = document.getElementById("auditRetentionDays");
                    if (retentionInput && data.retentionDays) retentionInput.value = data.retentionDays;

                    var pageInfo = document.getElementById("auditLogPageInfo");
                    if (pageInfo) pageInfo.textContent = page + " / " + totalPages;

                    if (logs.length === 0) {
                        contentDiv.innerHTML = "<p>" + (getMessage("auditLog-noLogs") || "No logs") + "</p>";
                        return;
                    }

                    var html = "<table class='jenkins-table'><thead><tr>" +
                        "<th>" + getMessage("auditLog-time") + "</th>" +
                        "<th>" + getMessage("auditLog-user") + "</th>" +
                        "<th>" + getMessage("auditLog-instance") + "</th>" +
                        "<th>" + getMessage("auditLog-jobs") + "</th>" +
                        "<th>" + getMessage("auditLog-result") + "</th>" +
                        "</tr></thead><tbody>";

                    logs.forEach(function (log) {
                        var actionLabel = log.action || "";
                        html += "<tr>" +
                            "<td>" + escapeHtml(log.formattedTimestamp || "") + "</td>" +
                            "<td>" + escapeHtml(log.username || "") + "</td>" +
                            "<td>" + escapeHtml(log.sourceInstance || "") + "</td>" +
                            "<td>" + escapeHtml(log.jobPathsSummary || "") + "</td>" +
                            "<td>" + actionLabel + " " + (log.successCount || 0) + "/" + (log.failureCount || 0) + "/" + (log.skippedCount || 0) + "</td>" +
                            "</tr>";
                    });

                    html += "</tbody></table>";
                    contentDiv.innerHTML = html;
                }
            })
            .catch(function () {
                contentDiv.innerHTML = "<p>Failed to load audit logs</p>";
            });
    }
})();
