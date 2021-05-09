package com.enioka.jqm.runner.shell;

import java.util.Dictionary;
import java.util.Hashtable;

import com.enioka.jqm.runner.api.JobRunner;

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

@Header(name = org.osgi.framework.Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator implements BundleActivator
{
    private ServiceRegistration<?> registration;

    public void start(BundleContext context) throws Exception
    {
        Dictionary<String, String> properties = new Hashtable<String, String>();
        properties.put("Plugin-Type", "JobRunner");
        properties.put("Runner-Type", "shell");
        registration = context.registerService(JobRunner.class, new ShellRunner(), properties);
    }

    public void stop(BundleContext context) throws Exception
    {
        registration.unregister();
    }
}
