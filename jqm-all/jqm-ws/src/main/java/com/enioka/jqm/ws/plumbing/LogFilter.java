package com.enioka.jqm.ws.plumbing;

import java.io.IOException;
import java.security.Principal;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class LogFilter implements Filter {
    static Logger log = LoggerFactory.getLogger("com.enioka.jqm.ws.request");

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("Initializing custom log filter inside WS app");
        // Nothing to do
    }

    @Override
    public void destroy() {
        // Nothing to do
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long t1 = System.nanoTime();
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Add username to log context if user is logged
        Principal p = req.getUserPrincipal();
        String username = p != null ? p.getName() : null;
        if (username != null && !username.trim().isEmpty()) {
            MDC.put("username", username);
        } else {
            MDC.put("username", "anonymous");
        }
        String userOsName = req.getRemoteUser();
        if (userOsName != null) {
            MDC.put("identity", userOsName);
        } else {
            MDC.put("identity", "-");
        }

        // Session
        HttpSession s = req.getSession(false);
        if (s != null) {
            MDC.put("sessionid", s.getId());
        } else {
            MDC.put("sessionid", "-1");
        }

        // IP
        MDC.put("ip", req.getRemoteAddr());

        // Go on, and clean at the end.
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("\"" + req.getMethod() + " " + req.getRequestURI() + " " + req.getProtocol() + "\" "
                    + res.getStatus() + " - " + ((System.nanoTime() - t1) / 1000000));
            MDC.clear();
        }
    }
}
