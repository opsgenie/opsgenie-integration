package com.opsgenie.plugin.controller;

import org.apache.commons.io.IOUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ConfigurationServlet extends HttpServlet {


    @Override
    protected final void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String body = IOUtils.toString(req.getInputStream());
    }

}
