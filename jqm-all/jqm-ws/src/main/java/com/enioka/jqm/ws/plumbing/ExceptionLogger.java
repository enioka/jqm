package com.enioka.jqm.ws.plumbing;

import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent.Type;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple JAX-RS event listener which simply logs every exception occurring during a JAX-RS call.
 */
public class ExceptionLogger implements ApplicationEventListener, RequestEventListener
{
    private static final Logger log = LoggerFactory.getLogger("RestClientException");

    @Override
    public void onEvent(final ApplicationEvent applicationEvent)
    {
    }

    @Override
    public RequestEventListener onRequest(final RequestEvent requestEvent)
    {
        return this;
    }

    @Override
    public void onEvent(RequestEvent paramRequestEvent)
    {
        if (paramRequestEvent.getType() == Type.ON_EXCEPTION)
        {
            log.info("a REST call failed with an exception", paramRequestEvent.getException());
        }
    }
}
