package com.enioka.jqm.ws.plumbing.shiro;

import javax.servlet.http.HttpServletRequest;

import org.apache.shiro.mgt.SessionStorageEvaluator;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.apache.shiro.web.util.WebUtils;;

public class SessionEvaluator implements SessionStorageEvaluator
{
    @Override
    public boolean isSessionStorageEnabled(Subject subject)
    {
        if (subject == null)
        {
            return false;
        }

        // If disabled in request (e.g. by using the noSessionCreation filter, it stays
        // disabled.
        if (WebUtils.isWeb(subject))
        {
            HttpServletRequest request = WebUtils.getHttpRequest(subject);
            Object o = request.getAttribute(DefaultSubjectContext.SESSION_CREATION_ENABLED);
            if (o != null && !((Boolean) o))
            {
                return false;
            }
        }

        // Then only allow humans, not API-only users, to create a session
        if (subject.hasRole("human"))
        {
            return true;
        }

        // By default, no sessions allowed.
        return false;
    }
}
