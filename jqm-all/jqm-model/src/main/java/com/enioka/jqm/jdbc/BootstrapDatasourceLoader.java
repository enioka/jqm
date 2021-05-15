package com.enioka.jqm.jdbc;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import com.enioka.jqm.cl.ExtClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * We do not want to depend on the JNDI plugin inside this bundle, which is very low-level and used everywhere including inside the client
 * libraries offered to end-users. This means it must have as little dependencies as possible. So we simply have a very basic data source
 * factory in this class, which can only use data from the local XML file.<br>
 * It looks for objet factories inside the bundle CL (which means application CL outside OSGi), as well as /ext if it exists.<br>
 * It is very static, but as is JNDI so no effort was made on this front.
 */
final class BootstrapDatasourceLoader
{
    private static Logger jqmlogger = LoggerFactory.getLogger(BootstrapDatasourceLoader.class);

    // Resource file contains at least the jqm jdbc connection definition. Static because JNDI root context is common to the whole JVM.
    private static String resourceFile = "resources.xml";
    private static boolean initialized = false;
    private static ClassLoader extClassLoader = ExtClassLoader.instance;

    /**
     * Content of the XML file (if it exists).
     */
    private static Map<String, Map<String, String>> xmlResourceDescriptions = new HashMap<>();

    /**
     * No construction is possible - static class.
     */
    private BootstrapDatasourceLoader()
    {}

    /**
     * If there is a local /ext directory, meaning we are running inside a JQM node and not an application server, try to load given
     * resource as a datasource. If the file exists but the resource is absent, fail. If file does not exist, return null - caller must
     * fetch its datasource from another source in that case (such as a JNDI context)
     *
     * @return
     */
    static synchronized DataSource getDatasourceFromXml(String jndiAlias) throws NamingException
    {
        if (extClassLoader == null)
        {
            jqmlogger.info(
                    "JQM API initialization has detected it is not running on a JQM node and will not try to create its own datasource but will fetch it from the underlying server");
            return null;
        }

        if (!initialized)
        {
            jqmlogger.info(
                    "JQM API initialization has detected it is running on a JQM node and will use its configuration file to create a datasource");
            importXml();
        }

        return getObjectInstance(jndiAlias);
    }

    private static void importXml() throws NamingException
    {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

        try (InputStream is = ClassLoader.getSystemResourceAsStream(resourceFile))
        {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(is);
            doc.getDocumentElement().normalize();

            NodeList nList = doc.getElementsByTagName("resource");

            for (int i = 0; i < nList.getLength(); i++)
            {
                Node n = nList.item(i);
                Map<String, String> resourcePrms = new HashMap<>();

                NamedNodeMap attrs = n.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++)
                {
                    Node attr = attrs.item(j);
                    String key = attr.getNodeName();
                    String value = attr.getNodeValue();

                    resourcePrms.put(key, value);
                }

                if (resourcePrms.isEmpty() || !resourcePrms.containsKey("name") || !resourcePrms.containsKey("type")
                        || !resourcePrms.containsKey("factory"))
                {
                    throw new NamingException(
                            "could not load the resource.xml file - file exists but a resource is missing compulsory data");
                }

                xmlResourceDescriptions.put(resourcePrms.get("name"), resourcePrms);
            }
        }
        catch (Exception e)
        {
            NamingException pp = new NamingException("could not initialize the JNDI local resources");
            pp.setRootCause(e);
            throw pp;
        }

        initialized = true;
    }

    private static DataSource getObjectInstance(String name) throws NamingException
    {
        // Get the description of the resource
        if (!xmlResourceDescriptions.containsKey(name))
        {
            throw new NamingException("No resource inside resource.xml named " + name);
        }
        Map<String, String> description = xmlResourceDescriptions.get(name);

        // Get class loader, we'll need it to load the factory class
        Class<?> factoryClass = null;
        ObjectFactory factory = null;

        try
        {
            factoryClass = extClassLoader.loadClass(description.get("factory"));
        }
        catch (ClassNotFoundException e)
        {
            NamingException ex = new NamingException("Could not find resource or resource factory class in the classpath");
            ex.initCause(e);
            throw ex;
        }
        catch (Exception e)
        {
            NamingException ex = new NamingException("Could not load resource or resource factory class for an unknown reason");
            ex.initCause(e);
            throw ex;
        }

        // Some providers use the CCL to load resources (especially when using ServiceProvider, as in tomcat-juli) so avoid
        // mixing CLs by explicitly setting it when using the context from the engine or tests. (not needed from payloads as they
        // already use a CL with ext as parent CL).
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(extClassLoader);

        Object result = null;
        try
        {
            try
            {
                factory = (ObjectFactory) factoryClass.newInstance();
            }
            catch (Exception e)
            {
                NamingException ex = new NamingException("Could not create resource factory instance");
                ex.initCause(e);
                throw ex;
            }

            try
            {
                Reference r = new Reference(description.get("type"));
                for (Map.Entry<String, String> e : description.entrySet())
                {
                    r.add(new StringRefAddr(e.getKey(), e.getValue()));
                }

                result = factory.getObjectInstance(r, null, null, null);
            }
            catch (Exception e)
            {
                NamingException ex = new NamingException(
                        "Could not create object resource from resource factory. JNDI definition & parameters may be incorrect.");
                ex.initCause(e);
                throw ex;
            }
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(prev);
        }

        return (DataSource) result;
    }
}
