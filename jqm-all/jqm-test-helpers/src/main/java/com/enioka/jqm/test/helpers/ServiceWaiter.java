package com.enioka.jqm.test.helpers;

import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper which spies on loaded service and allows to wait for a service to become activated. Mostly useful for simply logging what gets
 * activated. Waiting for services is only useful for complex test cases - simply injecting a service instance in the test class is usually
 * enough to wait for a service to be available.
 */
@Component(service = ServiceWaiter.class, scope = ServiceScope.SINGLETON)
public class ServiceWaiter implements ServiceListener
{
    public static Logger jqmlogger = LoggerFactory.getLogger(ServiceWaiter.class);

    private ConcurrentHashMap<String, Semaphore> sources = new ConcurrentHashMap<>();

    public void waitForService(String className)
    {
        jqmlogger.debug("Waiting for service {}", className);
        Semaphore s = sources.computeIfAbsent(className, key -> new Semaphore(0));
        try
        {
            s.acquire();
        }
        catch (InterruptedException e)
        {
            // test code
        }
        jqmlogger.debug("Done waiting for service {}", className);
    }

    @Override
    public void serviceChanged(ServiceEvent event)
    {
        String sKey = event.getServiceReference().toString();
        switch (event.getType())
        {
        case ServiceEvent.REGISTERED:
            StringBuilder logTmp = new StringBuilder();
            Enumeration<String> enumeration = event.getServiceReference().getProperties().keys();
            while (enumeration.hasMoreElements())
            {
                String k = enumeration.nextElement();
                logTmp.append(k);
                logTmp.append(": ");
                logTmp.append(event.getServiceReference().getProperties().get(k).toString());
                logTmp.append(", ");
            }
            jqmlogger.info("Service {} is registering - Properties {}", sKey, logTmp);

            Semaphore s = sources.computeIfAbsent(sKey, key -> new Semaphore(0));
            s.release();
            break;

        case ServiceEvent.UNREGISTERING:
            jqmlogger.info("Service {} is unregistering", sKey);

            s = sources.get(sKey);
            if (s == null || s.availablePermits() <= 0)
            {
                return;
            }

            try
            {
                s.acquire();
            }
            catch (InterruptedException e)
            {
                // test code.
            }
            break;

        default:
            // ignore other events
            break;

        }
    }

    @Activate
    public void onActivation(BundleContext ctx)
    {
        jqmlogger.info("Starting test service waiter");
        ctx.addServiceListener(this);
    }

    @Deactivate
    public void onDeactivation(BundleContext ctx)
    {
        sources.clear();
        ctx.removeServiceListener(this);
    }
}
