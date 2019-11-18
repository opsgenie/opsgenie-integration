(function ($) { // this closure helps us keep our variables to ourselves.
// This pattern is known as an "iife" - immediately invoked function expression

    // form the URL
    var url = AJS.contextPath() + "/opsgenie-apiKey/1.0/";

    // wait for the DOM (i.e., document "skeleton") to load. This likely isn't necessary for the current case,
    // but may be helpful for AJAX that provides secondary content.
    $(document).ready(function () {
        // request the config information from the server
        $.ajax({
            url: url,
            dataType: "json"
        }).done(function (apiKey) { // when the configuration is returned...
            // ...populate the form.
            $("#name").val(apiKey.key);
        });
    });

})(AJS.$ || jQuery);