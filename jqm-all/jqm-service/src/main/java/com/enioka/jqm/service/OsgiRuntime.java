package com.enioka.jqm.service;

import java.io.File;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

import com.enioka.jqm.cli.bootstrap.CommandLine;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OsgiRuntime
{
    private static Logger jqmlogger = LoggerFactory.getLogger(OsgiRuntime.class);

    static CommandLine newFramework()
    {
        jqmlogger.info("Initializing OSGi framework instance");

        System.setProperty("java.protocol.handler.pkgs", "org.ops4j.pax.url");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");

        File currentJar;
        try
        {
            currentJar = new File(OsgiRuntime.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }

        String rootDir = System.getProperty("com.enioka.jqm.service.osgi.rootdir");
        if (rootDir == null)
        {
            rootDir = currentJar.getParent();
        }

        String libPath = new File(rootDir, "bundle").getAbsolutePath();
        String tmpPath = new File(rootDir, "tmp/osgicache").getAbsolutePath();

        // Properties
        Map<String, String> osgiConfig = new HashMap<>();
        osgiConfig.put(Constants.FRAMEWORK_STORAGE, tmpPath);
        osgiConfig.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA, "com.enioka.jqm.cli.bootstrap;version=1.0.0");
        osgiConfig.put("felix.auto.deploy.action", ""); // Disable auto deploy
        osgiConfig.put("org.apache.aries.jax.rs.whiteboard.default.enabled", "false"); // Do not auto start web server
        osgiConfig.put("org.apache.felix.http.enable", "false");
        osgiConfig.put("org.apache.felix.https.enable", "false");

        // TODO: shutdown hook here.

        FrameworkFactory factory = null;
        try
        {
            factory = ServiceLoader.load(FrameworkFactory.class).iterator().next();
        }
        catch (NoSuchElementException e)
        {
            System.err.println("No available implementation of FrameworkFactory");
            e.printStackTrace();
            System.exit(999);
        }

        Framework framework = factory.newFramework(osgiConfig);
        try
        {
            framework.start();
            jqmlogger.info("Framework started");
        }
        catch (BundleException e)
        {
            jqmlogger.error("Could not initialize OSGi framework", e);
            System.exit(999);
        }

        // we prefer to use standard OSGi classes rather than Felix ones (which are cleaner)
        BundleContext ctx = framework.getBundleContext();

        // load our bundles
        jqmlogger.debug("Installing bundles...");
        for (String file : new File(libPath).list())
        {
            String path = new File(libPath, file).toURI().toString();
            tryInstallBundle(ctx, path);
        }

        // Start the bundles (& log)
        for (Bundle bundle : ctx.getBundles())
        {
            if (bundle != null && bundle.getSymbolicName() != null)
            {
                try
                {
                    bundle.start();
                }
                catch (BundleException e)
                {
                    jqmlogger.error("Could not start plugin " + bundle.getSymbolicName(), e);
                }

                jqmlogger.info(
                        "Bundle " + bundle.getSymbolicName() + " in version " + bundle.getVersion() + " is in state " + bundle.getState());
                if (bundle.getRegisteredServices() != null)
                {
                    for (ServiceReference<?> sr : bundle.getRegisteredServices())
                    {
                        jqmlogger.debug("\t\t " + sr.getClass().getCanonicalName());
                    }
                }
            }
        }

        // get our main entry point service
        ServiceReference<CommandLine> sr = ctx.getServiceReference(CommandLine.class);
        if (sr == null)
        {
            jqmlogger.error("Could not initialize OSGi framework - missing CLI implementation");
            System.exit(998);
        }

        CommandLine cli = ctx.getService(sr);
        if (cli == null)
        {
            jqmlogger.error("Could not initialize OSGi framework - CLI service reference was disabled during startup");
            System.exit(997);
        }

        return cli;
    }

    private static void tryInstallBundle(BundleContext ctx, String path)
    {
        try
        {
            jqmlogger.debug("\tInstalling bundle " + path);
            Bundle b = ctx.installBundle(path);

            if (b.getSymbolicName() == null)
            {
                b.uninstall();

                b = ctx.installBundle("wrap:" + path);
            }

            jqmlogger.debug("\t\t** Bundle installed as " + b.getSymbolicName() + ":" + b.getVersion());
        }
        catch (BundleException e)
        {
            jqmlogger.error("Cound not install bundle " + path, e);
            System.exit(996);
        }
    }
}
