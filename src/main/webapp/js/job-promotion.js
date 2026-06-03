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

    function getCrumbHeader() {
        var meta = document.querySelector('meta[name="crumbHeader"]');
        if (meta) {
            return meta.getAttribute("content");
        }
        if (typeof crumb !== "undefined" && crumb.headerName) {
            return crumb.headerName;
        }
        return "Jenkins-Crumb";
    }

    function getCrumbValue() {
        var meta = document.querySelector('meta[name="crumb"]');
        if (meta) {
            return meta.getAttribute("content");
        }
        if (typeof crumb !== "undefined" && crumb.value) {
            return crumb.value;
        }
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
    var jobListSection = document.getElementById("jobListSection");
    var promotionOptions = document.getElementById("promotionOptions");
    var jobTableBody = document.getElementById("jobTableBody");
    var headerCheckbox = document.getElementById("headerCheckbox");

    var remoteJobs = [];

    if (loadJobsBtn) {
        loadJobsBtn.addEventListener("click", function () {
            var folderPath = document.getElementById("folderPathInput").value.trim();
            loadJobsBtn.disabled = true;
            var originalText = loadJobsBtn.getAttribute("data-original-text") || loadJobsBtn.textContent;
            loadJobsBtn.textContent = "Loading...";

            var formData = new FormData();
            formData.append("folderPath", folderPath);

            var headers = buildHeaders();

            fetch(actionUrl + "/listRemoteJobs", {
                method: "POST",
                headers: headers,
                body: formData,
                credentials: "same-origin",
            })
                .then(function (response) {
                    return response.text();
                })
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
        jobs.forEach(function (job, index) {
            var tr = document.createElement("tr");
            var typeLabel = job.folder ? "Folder" : "Job";

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
            checkboxes.forEach(function (cb) {
                cb.checked = true;
            });
            if (headerCheckbox) headerCheckbox.checked = true;
        });
    }

    if (deselectAllBtn) {
        deselectAllBtn.addEventListener("click", function () {
            var checkboxes = document.querySelectorAll(".job-checkbox");
            checkboxes.forEach(function (cb) {
                cb.checked = false;
            });
            if (headerCheckbox) headerCheckbox.checked = false;
        });
    }

    if (promoteBtn) {
        promoteBtn.addEventListener("click", function () {
            var checked = document.querySelectorAll(".job-checkbox:checked");
            if (checked.length === 0) {
                alert("Please select at least one job to promote");
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
            var typeIcon = isFolder ? "📁 " : "📄 ";
            jobListHtml += "<li>" + typeIcon + escapeHtml(displayName) + "</li>";
        });
        jobListHtml += "</ul>";

        dialog.innerHTML =
            "<h2>Confirm Promotion</h2>" +
            "<p>The following jobs will be promoted:</p>" +
            jobListHtml +
            "<p>Mode: " +
            (forceUpdate ? "Force Update" : "Normal Update") +
            "</p>" +
            "<div class='jp-dialog-actions'>" +
            "<button class='jenkins-button jenkins-button--primary' id='confirmPromoteBtn'>Confirm Promote</button>" +
            "<button class='jenkins-button' id='cancelPromoteBtn'>Cancel</button>" +
            "</div>";

        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        document.getElementById("cancelPromoteBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
        });

        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) {
                document.body.removeChild(overlay);
            }
        });

        document.getElementById("confirmPromoteBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
            executePromotion(selectedJobs, forceUpdate);
        });
    }

    function executePromotion(selectedJobs, forceUpdate) {
        promoteBtn.disabled = true;
        var originalText = promoteBtn.getAttribute("data-original-text") || promoteBtn.textContent;
        promoteBtn.textContent = "Promoting...";

        var formData = new FormData();
        formData.append("jobs", selectedJobs.join(","));
        formData.append("forceUpdate", forceUpdate ? "true" : "false");

        var headers = buildHeaders();

        fetch(actionUrl + "/promoteJobs", {
            method: "POST",
            headers: headers,
            body: formData,
            credentials: "same-origin",
        })
            .then(function (response) {
                return response.text();
            })
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

        var resultHtml = "<table class='jenkins-table'><thead><tr><th>Job</th><th>Status</th><th>Message</th></tr></thead><tbody>";
        results.forEach(function (r) {
            var statusClass = "jp-status-" + r.status.toLowerCase();
            resultHtml +=
                "<tr>" +
                "<td>" +
                escapeHtml(r.jobFullPath) +
                "</td>" +
                "<td class='" +
                statusClass +
                "'>" +
                r.status +
                "</td>" +
                "<td>" +
                escapeHtml(r.message || "") +
                "</td>" +
                "</tr>";
        });
        resultHtml += "</tbody></table>";

        dialog.innerHTML =
            "<h2>Promotion Results</h2>" +
            resultHtml +
            "<div class='jp-dialog-actions'>" +
            "<button class='jenkins-button jenkins-button--primary' id='closeResultBtn'>Close</button>" +
            "</div>";

        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        document.getElementById("closeResultBtn").addEventListener("click", function () {
            document.body.removeChild(overlay);
        });

        overlay.addEventListener("click", function (e) {
            if (e.target === overlay) {
                document.body.removeChild(overlay);
            }
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
