/**
 * Copyright © 2013 enioka. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enioka.jqm.clusternode;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.engine.JqmInitError;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.pki.JdbcCa;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Every engine has an embedded Jetty engine that serves the different web service APIs.
 */
class JettyServer
{
    private static Logger jqmlogger = LoggerFactory.getLogger(JettyServer.class);

    private Node node;
    private List<String> startedServicesPid = new ArrayList<>(3);

    // TODO: certificate authentication, certificate store.

    void start(Node node, DbConn cnx, ConfigurationAdmin adminService)
    {
        this.node = node;
        Configuration httpServiceConfiguration;
        try
        {
            httpServiceConfiguration = adminService.getConfiguration("org.apache.felix.http", null);
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not access OSGi registry storage", e);
        }
        Dictionary<String, Object> httpServiceProperties = httpServiceConfiguration.getProperties();
        if (httpServiceProperties == null)
        {
            httpServiceProperties = new Hashtable<String, Object>();
        }

        ///////////////////////////////////////////////////////////////////////
        // Configuration checks
        ///////////////////////////////////////////////////////////////////////

        // Only load Jetty if web APIs are allowed in the cluster
        boolean startJetty = !Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "disableWsApi", "false"));
        if (!startJetty)
        {
            jqmlogger.info("Jetty will not start - parameter disableWsApi is set to true");
            return;
        }

        // Only load Jetty if at least one war is present...
        File war = new File("./webapp/jqm-ws.war");
        if (!war.exists() || !war.isFile())
        {
            jqmlogger.info("Jetty will not start - there are no web applications to load inside the webapp directory");
            return;
        }

        // Only load Jetty if at least one application should start
        if (!node.getLoadApiAdmin() && !node.getLoadApiClient() && !node.getLoapApiSimple())
        {
            jqmlogger.info("Jetty will not start - all web APIs are disabled on this node");
            return;
        }

        // Get which APIs should start
        boolean loadApiSimple = node.getLoapApiSimple();
        boolean loadApiClient = node.getLoadApiClient();
        boolean loadApiAdmin = node.getLoadApiAdmin();

        // Port - also update node if no port specified.
        if (node.getPort() == 0)
        {
            node.setPort(getRandomFreePort());

            cnx.runUpdate("node_update_port_by_id", node.getPort(), node.getId());
            cnx.commit();
        }
        boolean useSsl = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableWsApiSsl", "true"));

        // Certificates
        boolean useInternalPki = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableInternalPki", "true"));
        String pfxPassword = GlobalParameter.getParameter(cnx, "pfxPassword", "SuperPassword");

        ///////////////////////////////////////////////////////////////////////
        // Certificates preparation
        ///////////////////////////////////////////////////////////////////////

        // Update stores from database? (or create them completely)
        if (useSsl && useInternalPki)
        {
            jqmlogger.info("JQM will use its internal PKI for all certificates as parameter enableInternalPki is 'true'");
            JdbcCa.prepareWebServerStores(cnx, "CN=" + node.getDns(), "./conf/keystore.pfx", "./conf/trusted.jks", pfxPassword,
                    node.getDns(), "./conf/server.cer", "./conf/ca.cer");
        }

        if (useSsl)
        {
            // Keystore for HTTPS connector
            httpServiceProperties.put("org.apache.felix.https.keystore", "./conf/keystore.pfx");
            httpServiceProperties.put("org.apache.felix.https.keystore.password", pfxPassword);

            // Trust store
            httpServiceProperties.put("org.apache.felix.https.truststore", "./conf/trusted.jks");
            httpServiceProperties.put("org.apache.felix.https.truststore.password", pfxPassword);
            httpServiceProperties.put("org.apache.felix.https.truststore.type", "JKS");

            // Client certificate authentication
            httpServiceProperties.put("org.apache.felix.https.clientcertificate", "want");
        }

        ///////////////////////////////////////////////////////////////////////
        // Create a configuration for each JAXRS API to start
        ///////////////////////////////////////////////////////////////////////

        if (loadApiSimple)
        {
            activateApiJaxRsService(adminService, "com.enioka.jqm.ws.api.ServiceSimple");
        }
        if (loadApiClient)
        {
            activateApiJaxRsService(adminService, "com.enioka.jqm.ws.api.ServiceClient");
        }
        if (loadApiAdmin)
        {
            activateApiJaxRsService(adminService, "com.enioka.jqm.ws.api.ServiceAdmin");
        }

        ///////////////////////////////////////////////////////////////////////
        // Jetty configuration
        ///////////////////////////////////////////////////////////////////////

        // HTTP configuration
        httpServiceProperties.put("org.apache.felix.http.jetty.responseBufferSize", 32768);
        httpServiceProperties.put("org.apache.felix.http.jetty.headerBufferSize", 8192);
        httpServiceProperties.put("org.apache.felix.http.jetty.sendServerHeader", false);

        // This is a JQM node
        httpServiceProperties.put("servlet.init.jqmnodeid", node.getId().toString());

        // Connectors configuration - only start http or https, but not both
        httpServiceProperties.put("org.apache.felix.https.enable", useSsl);
        httpServiceProperties.put("org.apache.felix.http.enable", !useSsl);
        if (!useSsl)
        {
            httpServiceProperties.put("org.osgi.service.http.port", node.getPort());

            jqmlogger.info("JQM will use plain HTTP for all communications (no TLS)");
        }
        else
        {
            httpServiceProperties.put("org.osgi.service.http.port.secure", node.getPort());
            httpServiceProperties.put("org.apache.felix.https.jetty.ciphersuites.excluded",
                    "SSL_RSA_WITH_DES_CBC_SHA,SSL_DHE_RSA_WITH_DES_CBC_SHA,SSL_DHE_DSS_WITH_DES_CBC_SHA,SSL_RSA_EXPORT_WITH_RC4_40_MD5,"
                            + "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA,SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA,SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA,"
                            + "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,SSL_RSA_WITH_3DES_EDE_CBC_SHA,"
                            + "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA256,"
                            + "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,"
                            + "TLS_RSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,"
                            + "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,TLS_DHE_RSA_WITH_AES_128_CBC_SHA,TLS_DHE_DSS_WITH_AES_128_CBC_SHA,"
                            + "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA,TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,"
                            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA,TLS_DHE_DSS_WITH_AES_256_CBC_SHA,TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,TLS_RSA_WITH_AES_256_GCM_SHA384,TLS_RSA_WITH_AES_256_CBC_SHA256,TLS_RSA_WITH_AES_256_CBC_SHA");
            httpServiceProperties.put("org.apache.felix.https.jetty.protocols.included", "TLSv1.2");

            jqmlogger.info("JQM will use TLS for all HTTP communications as parameter enableWsApiSsl is 'true'");
        }
        jqmlogger.debug("Jetty will bind on port {}", node.getPort());

        ///////////////////////////////////////////////////////////////////////
        // JAX-RS whiteboard configuration
        ///////////////////////////////////////////////////////////////////////

        Configuration jaxRsServiceConfiguration;
        try
        {
            jaxRsServiceConfiguration = adminService.getConfiguration("org.apache.aries.jax.rs.whiteboard.default", null);
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not access OSGi registry storage", e);
        }
        Dictionary<String, Object> jaxRsServiceProperties = jaxRsServiceConfiguration.getProperties();
        if (jaxRsServiceProperties == null)
        {
            jaxRsServiceProperties = new Hashtable<String, Object>();
        }

        jaxRsServiceProperties.put("enabled", true);
        jaxRsServiceProperties.put("default.application.base", "/ws");

        ///////////////////////////////////////////////////////////////////////
        // Done with Jetty itself - give configuration to the admin service.
        ///////////////////////////////////////////////////////////////////////
        try
        {
            jqmlogger.debug("Starting HTTP service configuration and reset");
            jaxRsServiceConfiguration.update(jaxRsServiceProperties);
            httpServiceConfiguration.update(httpServiceProperties);
            // Thread.sleep(3000);
            jqmlogger.debug("HTTP service configuration and reset are done");
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not modify OSGi registry storage", e);
        }

    }

    void stop(ConfigurationAdmin adminService)
    {
        Configuration configuration;
        try
        {
            configuration = adminService.getConfiguration("org.apache.felix.http");
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not access OSGi registry storage", e);
        }
        if (configuration == null || configuration.getProperties() == null)
        {
            // Was not actually started or bundles absent.
            return;
        }
        Dictionary<String, Object> properties = configuration.getProperties();
        properties.put("org.apache.aries.jax.rs.whiteboard.default.enabled", false);
        properties.put(".default.application.base", "/ws");
        properties.put("org.apache.felix.http.enable", false);

        try
        {
            configuration.update(properties);
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not modify OSGi registry storage", e);
        }

        cleanApiJaxRsServices(adminService);
    }

    private void activateApiJaxRsService(ConfigurationAdmin adminService, String configPid)
    {
        Configuration jaxRsServiceConfig;
        try
        {
            jaxRsServiceConfig = adminService.getFactoryConfiguration(configPid, node.getId() + "", null);
            // Final null is important - it means the configuration is attached to the first
            // bundle with a comaptible service
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not fetch OSGi " + configPid + " configuration factory", e);
        }
        Dictionary<String, Object> jaxRsServiceProperties = jaxRsServiceConfig.getProperties();
        if (jaxRsServiceProperties == null)
        {
            jaxRsServiceProperties = new Hashtable<>();
        }
        jaxRsServiceProperties.put("jqmnodeid", node.getId());

        try
        {
            jaxRsServiceConfig.update(jaxRsServiceProperties);
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not modify OSGi registry storage", e);
        }

        startedServicesPid.add(jaxRsServiceConfig.getPid());
        jqmlogger.info("Created configuration for service " + configPid);
    }

    private void cleanApiJaxRsServices(ConfigurationAdmin adminService)
    {
        Configuration config = null;
        for (String pid : startedServicesPid)
        {
            try
            {
                config = adminService.getConfiguration(pid);
            }
            catch (IOException e)
            {
                // Ignore - service already dead on shutdown.
                continue;
            }

            try
            {
                config.delete();
            }
            catch (IOException e)
            {
                jqmlogger.warn("Could not remove JAXRS service from configuration", e);
            }
        }
    }

    private int getRandomFreePort()
    {
        int port = -1;
        ServerSocket ss = null;
        try
        {
            ss = new ServerSocket(0);
            port = ss.getLocalPort();
        }
        catch (IOException e)
        {
            throw new JqmInitError("Could not determine a free TCP port", e);
        }
        finally
        {
            Helpers.closeQuietly(ss);
        }
        return port;
    }
}

/*
 * boolean useSsl = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableWsApiSsl", "true")); boolean useInternalPki =
 * Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableInternalPki", "true")); String pfxPassword =
 * GlobalParameter.getParameter(cnx, "pfxPassword", "SuperPassword"); String bindTo = node.getDns().trim().toLowerCase();
 *
 * /////////////////////////////////////////////////////////////////////// // Jetty configuration
 * ///////////////////////////////////////////////////////////////////////
 *
 * // Setup thread pool QueuedThreadPool threadPool = new QueuedThreadPool(); threadPool.setMaxThreads(10);
 *
 * // Create server server = new Server(threadPool);
 *
 * server.setDumpAfterStart(false); server.setDumpBeforeStop(false); server.setStopAtShutdown(true);
 *
 * // HTTP configuration HttpConfiguration httpConfig = new HttpConfiguration(); httpConfig.setSecureScheme("https");
 * httpConfig.setOutputBufferSize(32768); httpConfig.setRequestHeaderSize(8192); httpConfig.setResponseHeaderSize(8192);
 * httpConfig.setSendServerVersion(false); httpConfig.setSendDateHeader(false); if (useSsl) { httpConfig.setSecurePort(node.getPort()); }
 *
 * // TLS configuration SslContextFactory scf = null; if (useSsl) { jqmlogger.
 * info("JQM will use TLS for all HTTP communications as parameter enableWsApiSsl is 'true'" );
 *
 * // Certificates if (useInternalPki) { jqmlogger.
 * info("JQM will use its internal PKI for all certificates as parameter enableInternalPki is 'true'" ); JdbcCa.prepareWebServerStores(cnx,
 * "CN=" + node.getDns(), "./conf/keystore.pfx", "./conf/trusted.jks", pfxPassword, node.getDns(), "./conf/server.cer", "./conf/ca.cer"); }
 * scf = new SslContextFactory("./conf/keystore.pfx");
 *
 * scf.setKeyStorePassword(pfxPassword); scf.setKeyStoreType("PKCS12");
 *
 * scf.setTrustStorePath("./conf/trusted.jks"); scf.setTrustStorePassword(pfxPassword); scf.setTrustStoreType("JKS");
 *
 * // Ciphers scf.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA", "SSL_DHE_RSA_WITH_DES_CBC_SHA", "SSL_DHE_DSS_WITH_DES_CBC_SHA",
 * "SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
 * "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA", "SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
 * "SSL_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA", "TLS_RSA_WITH_AES_128_GCM_SHA256",
 * "TLS_RSA_WITH_AES_128_CBC_SHA256", "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
 * "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
 * "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA", "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
 * "TLS_DHE_DSS_WITH_AES_128_CBC_SHA", "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA");
 *
 * // We allow client certificate authentication. scf.setWantClientAuth(true); scf.setEndpointIdentificationAlgorithm(null); // Means no
 * hostname check, as client certificates do not sign a hostname but an // identity.
 *
 * // Servlet TLS attributes httpConfig = new HttpConfiguration(httpConfig); httpConfig.addCustomizer(new SecureRequestCustomizer());
 *
 * // Connectors. ServerConnector https = new ServerConnector(server, scf, new HttpConnectionFactory(httpConfig));
 * https.setPort(node.getPort()); https.setIdleTimeout(30000); https.setHost(bindTo); server.addConnector(https);
 *
 * jqmlogger.debug("Jetty will bind on interface {} on port {} with HTTPS", bindTo, node.getPort()); } else {
 * jqmlogger.info("JQM will use plain HTTP for all communications (no TLS)");
 *
 * // Connectors. ServerConnector http = new ServerConnector(server, new HttpConnectionFactory(httpConfig)); http.setPort(node.getPort());
 * http.setIdleTimeout(30000); http.setHost(bindTo); server.addConnector(http);
 *
 * jqmlogger.debug("Jetty will bind on interface {} on port {} with HTTP", bindTo, node.getPort()); }
 *
 * // Collection handler server.setHandler(handlers);
 *
 * // Load the webapp context loadWar(cnx);
 *
 * // Start the server jqmlogger.trace("Starting Jetty (port " + node.getPort() + ")"); try { server.start(); } catch (BindException e) { //
 * JETTY-839: threadpool not daemon nor close on exception. Explicit closing required as workaround. this.stop(); throw new
 * JqmInitError("Could not start web server - check there is no other process binding on this port & interface" , e); } catch (Exception e)
 * { throw new JqmInitError("Could not start web server - not a port issue, but a generic one" , e); }
 *
 * // Save port if it was generated randomly if (node.getPort() == 0) { // New nodes are created with a non-assigned port.
 * cnx.runUpdate("node_update_port_by_id", getActualPort(), node.getId()); node.setPort(getActualPort()); // refresh in-memory object too.
 * cnx.commit(); }
 *
 * // Done jqmlogger.info("Jetty has started on port " + getActualPort()); }
 *
 * int getActualPort() { if (server == null) { return 0; } return ((NetworkConnector) server.getConnectors()[0]).getLocalPort(); }
 *
 * void stop() { if (server == null) { return; } jqmlogger.trace("Jetty will now stop"); try { for (Handler ha : server.getHandlers()) {
 * ha.stop(); ha.destroy(); handlers.removeHandler(ha); }
 *
 * this.server.stop(); this.server.join(); this.server.destroy(); this.server = null; jqmlogger.info("Jetty has stopped"); } catch
 * (Exception e) { jqmlogger.error(
 * "An error occured during Jetty stop. It is not an issue if it happens during JQM node shutdown, but one during restart (memeory leak)." ,
 * e); } }
 *
 * private void loadWar(DbConn cnx) { File war = new File("./webapp/jqm-ws.war"); if (!war.exists() || !war.isFile()) { return; }
 * jqmlogger.info("Jetty will now load the web service application war");
 *
 * // Load web application. webAppContext = new WebAppContext(war.getPath(), "/"); webAppContext.setDisplayName("JqmWebServices");
 *
 * webAppContext.getSystemClasspathPattern().add( "javax.servlet.ServletContainerInitializer");
 *
 * // Hide server classes from the web app webAppContext.getServerClasspathPattern().add("com.enioka.jqm.api.", "com.enioka.api.admin."); //
 * engine and webapp can have // different API implementations // (during tests mostly)
 * webAppContext.getServerClasspathPattern().add("com.enioka.jqm.tools."); // The engine itself should not be exposed to the webapp.
 * webAppContext.getServerClasspathPattern().add( "-com.enioka.jqm.tools.JqmXmlException"); // inside XML bundle, not engine.
 * webAppContext.getServerClasspathPattern().add( "-com.enioka.jqm.tools.XmlJobDefExporter");
 *
 * // JQM configuration should be on the class path webAppContext.setExtraClasspath("conf/jqm.properties");
 * webAppContext.setInitParameter("jqmnode", node.getName()); webAppContext.setInitParameter("jqmnodeid", node.getId().toString());
 * webAppContext.setInitParameter("enableWsApiAuth", GlobalParameter.getParameter(cnx, "enableWsApiAuth", "true"));
 *
 * // Set configurations (order is important: need to unpack war before reading web.xml) webAppContext.setConfigurations(new Configuration[]
 * { new WebInfConfiguration(), new WebXmlConfiguration(), new MetaInfConfiguration(), new FragmentConfiguration(), new
 * AnnotationConfiguration() });
 *
 * handlers.addHandler(webAppContext); }
 */
