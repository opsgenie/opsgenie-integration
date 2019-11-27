AJS.$(document).ready(function () {
    AJS.$("#project-select").auiSelect2({
        placeholder: "Select a project",
        allowClear: true
    });
    AJS.$(".og-tooltip").each(function () {
        AJS.$(this).tooltip();
    });
    AJS.inlineHelp();
});