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
            loadJobsBtn.disabled = true;
            var originalText = loadJobsBtn.getAttribute("data-original-text") || loadJobsBtn.textContent;
            loadJobsBtn.textContent = getMessage("loading") || "Loading...";

            var formData = new FormData();
            // For folder pages, don't send folderPath - backend uses current folder
            // For root page, send the folderPath input value
            if (!isFolderPage()) {
                var folderPath = document.getElementById("folderPathInput").value.trim();
                formData.append("folderPath", folderPath);
            }

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
            var typeIcon = isFolder ? "📁 " : "📄 ";
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
        promoteBtn.textContent = getMessage("loading") || "Promoting...";

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

        var resultHtml = "<table class='jenkins-table'><thead><tr><th>" + getMessage("result-job") + "</th><th>" + getMessage("result-status") + "</th><th>" + getMessage("result-message") + "</th></tr></thead><tbody>";
        results.forEach(function (r) {
            var statusClass = "jp-status-" + r.status.toLowerCase();
            var statusText = r.status;
            if (r.status === "SUCCESS") {
                statusText = getMessage("success");
            } else if (r.status === "SKIPPED") {
                statusText = getMessage("skipped");
            } else if (r.status === "FAILURE") {
                statusText = getMessage("failure");
            }
            resultHtml +=
                "<tr>" +
                "<td>" +
                escapeHtml(r.jobFullPath) +
                "</td>" +
                "<td class='" +
                statusClass +
                "'>" +
                statusText +
                "</td>" +
                "<td>" +
                escapeHtml(r.message || "") +
                "</td>" +
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
