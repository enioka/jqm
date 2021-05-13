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
package com.enioka.jqm.integration.tests;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.ops4j.pax.exam.CoreOptions.wrappedBundle;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.clusternode.EngineCallback;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.engine.JqmEngineFactory;
import com.enioka.jqm.engine.JqmEngineOperations;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.DbManager;
import com.enioka.jqm.test.helpers.DebugHsqlDbServer;
import com.enioka.jqm.test.helpers.ServiceWaiter;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.apache.shiro.util.Assert;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JqmBaseTest
{
    public static Logger jqmlogger = LoggerFactory.getLogger(JqmBaseTest.class);

    protected static Db db;

    public Map<String, JqmEngineOperations> engines = new HashMap<>();
    public List<DbConn> cnxs = new ArrayList<>();
    protected DbConn cnx;

    @Inject
    protected static BundleContext bundleContext;

    @Inject
    protected DebugHsqlDbServer s;

    @Inject
    ConfigurationAdmin adminService;

    @Inject
    ServiceWaiter serviceWaiter;

    JqmClient jqmClient;

    @Rule
    public TestName testName = new TestName();

    @Configuration
    public Option[] config()
    {
        Option[] res = new Option[] {
                // OSGi DECLARATIVE SERVICES
                mavenBundle("org.osgi", "org.osgi.service.cm").versionAsInProject(),
                mavenBundle("org.apache.felix", "org.apache.felix.scr").versionAsInProject(),
                mavenBundle("org.osgi", "org.osgi.util.promise").versionAsInProject(),
                mavenBundle("org.osgi", "org.osgi.util.function").versionAsInProject(),

                // OSGi configuration service
                mavenBundle("org.apache.felix", "org.apache.felix.configadmin").versionAsInProject(),

                // Our test database (for most tests)
                mavenBundle("org.hsqldb", "hsqldb").versionAsInProject(),

                // Apache commons
                mavenBundle("commons-io", "commons-io").versionAsInProject(),
                mavenBundle("commons-lang", "commons-lang", "2.6").versionAsInProject(),
                mavenBundle("org.apache.commons", "commons-lang3", "3.11").versionAsInProject(),
                mavenBundle("org.apache.httpcomponents", "httpclient-osgi").versionAsInProject(),
                mavenBundle("org.apache.httpcomponents", "httpcore-osgi").versionAsInProject(),

                // Cron
                wrappedBundle(mavenBundle("it.sauronsoftware.cron4j", "cron4j").versionAsInProject()),

                // CLI
                wrappedBundle(mavenBundle("com.beust", "jcommander").versionAsInProject()),

                // LOG
                mavenBundle("commons-logging", "commons-logging").versionAsInProject(),
                mavenBundle("org.slf4j", "slf4j-api").versionAsInProject(),
                mavenBundle("ch.qos.logback", "logback-core").versionAsInProject(),
                mavenBundle("ch.qos.logback", "logback-classic").versionAsInProject(),

                // XML & binding through annotations
                wrappedBundle(mavenBundle("org.jdom", "jdom").versionAsInProject()), //
                mavenBundle("jakarta.activation", "jakarta.activation-api").versionAsInProject(),
                mavenBundle("jakarta.xml.bind", "jakarta.xml.bind-api").versionAsInProject(), // JAXB
                mavenBundle("org.apache.geronimo.specs", "geronimo-annotation_1.3_spec").versionAsInProject(),

                // Needed on Java8 to kill processes properly (inside shell runner)
                wrappedBundle(mavenBundle("org.jvnet.winp", "winp").versionAsInProject()),

                // Shiro is needed by test helpers & client lib for password generation
                mavenBundle("org.apache.shiro", "shiro-core").versionAsInProject(),

                // Needed for certificate init on main service startup.
                mavenBundle("org.bouncycastle", "bcpkix-jdk15on").versionAsInProject(),
                mavenBundle("org.bouncycastle", "bcprov-jdk15on").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-pki").versionAsInProject(),

                // JQM tested libraries
                mavenBundle("com.enioka.jqm", "jqm-cli").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-cli-bootstrap").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-clusternode").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-impl-hsql").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-impl-pg").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-api").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-loader").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-api-client-core").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-api-client-jdbc").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-xml").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-service").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-admin").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-model").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-impl-hsql").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-impl-pg").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-engine").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-runner-api").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-runner-java").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-runner-shell").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-test-helpers").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-jndi-context").versionAsInProject(),
                mavenBundle("com.enioka.jqm", "jqm-integration-tests").versionAsInProject(),

                // Junit itself
                junitBundles(),

                // Log config file
                systemProperty("logback.configurationFile")
                        .value("file:" + Paths.get("../jqm-service/target/classes/logback.xml").toAbsolutePath().normalize().toString()),

                // Maven config
                systemProperty("org.ops4j.pax.url.mvn.repositories").value("https://repo1.maven.org/maven2@id=central"),
                systemProperty("org.ops4j.pax.url.mvn.useFallbackRepositories").value("false"),

        };

        Option[] additionnal = moreOsgiconfig();
        int localOptionsCount = res.length;
        res = java.util.Arrays.copyOf(res, localOptionsCount + additionnal.length);
        for (int i = 0; i < additionnal.length; i++)
        {
            res[localOptionsCount + i] = additionnal[i];
        }

        return res;
    }

    /**
     * To be optionaly overloaded by tests.
     */
    protected Option[] moreOsgiconfig()
    {
        return options();
    }

    /**
     * Separated web configuration (as it is rather heavy only load it inside {@link #moreOsgiconfig()} if needed)
     */
    protected Option[] webConfig()
    {
        return options(
                // OSGi HTTP Whiteboard (based on Jetty, providing OSGi HTTP service, used by JAX-RS whiteboard)
                mavenBundle("org.apache.felix", "org.apache.felix.http.servlet-api").versionAsInProject(),
                mavenBundle("org.apache.felix", "org.apache.felix.http.jetty").versionAsInProject(),
                mavenBundle("org.osgi", "org.osgi.service.http.whiteboard").versionAsInProject(),

                // OSGi JAX-RS whiteboard (based on CXF, with full useless SOAP implementation)
                mavenBundle("org.osgi", "org.osgi.service.jaxrs").versionAsInProject(),
                mavenBundle("org.apache.aries.jax.rs", "org.apache.aries.jax.rs.whiteboard").versionAsInProject(),
                mavenBundle("org.apache.aries.spec", "org.apache.aries.javax.jax.rs-api").versionAsInProject(),
                mavenBundle("jakarta.xml.ws", "jakarta.xml.ws-api").versionAsInProject(),
                mavenBundle("jakarta.xml.soap", "jakarta.xml.soap-api").versionAsInProject(),
                mavenBundle("jakarta.annotation", "jakarta.annotation-api").versionAsInProject(),

                systemProperty("org.apache.felix.http.enable").value("false"),
                systemProperty("org.apache.felix.https.enable").value("false"), //
                systemProperty("org.osgi.service.http.port").value("-1"),
                systemProperty("org.apache.aries.jax.rs.whiteboard.default.enabled").value("false"),

                // Web security
                mavenBundle("org.apache.shiro", "shiro-core").versionAsInProject(),
                mavenBundle("org.apache.shiro", "shiro-web").versionAsInProject(),

                // Our web project
                mavenBundle("com.enioka.jqm", "jqm-ws").type("war").versionAsInProject() // Yes this is a war - actually a wab - deployment
        );
    }

    @Before
    public void beforeEachTest() throws NamingException
    {
        jqmlogger.debug("**********************************************************");
        jqmlogger.debug("Starting test " + testName.getMethodName());

        JqmClientFactory.resetClient(null);
        jqmClient = JqmClientFactory.getClient();

        if (db == null)
        {
            // In all cases load the datasource. (the helper itself will load the property file if any).
            db = DbManager.getDb();
        }

        cnx = getNewDbSession();
        TestHelpers.cleanup(cnx);
        TestHelpers.createTestData(cnx);
        cnx.commit();

        // Force JNDI directory loading
        InitialContext.doLookup("string/debug");
    }

    @After
    public void afterEachTest()
    {
        jqmlogger.debug("*** Cleaning after test " + testName.getMethodName());
        for (String k : engines.keySet())
        {
            JqmEngineOperations e = engines.get(k);
            e.stop();
        }
        engines.clear();
        for (DbConn cnx : cnxs)
        {
            cnx.close();
        }
        cnxs.clear();

        // Reset the caches - no side effect between tests?
        try
        {
            InitialContext.doLookup("internal://reset");
        }
        catch (NamingException e)
        {
            // jqmlogger.warn("Could not purge test JNDI context", e);
        }

        // Java 6 GC being rather inefficient, we must run it multiple times to correctly collect Jetty-created class loaders and avoid
        // permgen issues
        System.runFinalization();
        System.gc();
        System.runFinalization();
        System.gc();
        System.gc();
    }

    protected void AssumeWindows()
    {
        Assume.assumeTrue(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    protected void AssumeNotWindows()
    {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win"));
    }

    protected void AssumeHsqldb()
    {
        Assume.assumeTrue(s.isHsqldb());
    }

    protected boolean onWindows()
    {
        return System.getProperty("os.name").toLowerCase().startsWith("win");
    }

    protected JqmEngineOperations addAndStartEngine()
    {
        return addAndStartEngine("localhost");
    }

    protected JqmEngineOperations addAndStartEngine(String nodeName)
    {
        beforeStartEngine(nodeName);
        JqmEngineOperations e = JqmEngineFactory.startEngine(nodeName, new EngineCallback(adminService));
        engines.put(nodeName, e);
        afterStartEngine(nodeName);
        return e;
    }

    protected void beforeStartEngine(String nodeName)
    {
        // For overrides.
    }

    protected void afterStartEngine(String nodeName)
    {
        // For overrides.
    }

    protected void stopAndRemoveEngine(String nodeName)
    {
        JqmEngineOperations e = engines.get(nodeName);
        e.stop();
        engines.remove(nodeName);
    }

    protected DbConn getNewDbSession()
    {
        DbConn cnx = db.getConn();
        cnxs.add(cnx);
        return cnx;
    }

    protected void sleep(int s)
    {
        sleepms(1000 * s);
    }

    protected static void sleepms(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            // not an issue in tests
        }
    }

    protected void simulateDbFailure()
    {
        if (db.getProduct().contains("hsql"))
        {
            jqmlogger.info("DB is going down");
            s.stop();
            jqmlogger.info("DB is now fully down");
            this.sleep(1);
            jqmlogger.info("Restarting DB");
            s.start();
        }
        else if (db.getProduct().contains("postgresql"))
        {
            try
            {
                // update pg_database set datallowconn = false where datname = 'jqm' // Cannot run, as we cannot reconnect afterward!
                cnx.runRawSelect("select pg_terminate_backend(pid) from pg_stat_activity where datname='jqm';");
            }
            catch (Exception e)
            {
                // Do nothing - the query is a suicide so it cannot work fully.
            }
            Helpers.closeQuietly(cnx);
            cnx = getNewDbSession();
        }
    }

    protected void displayAllHistoryTable()
    {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm:ss.SSS");
        jqmlogger.debug("==========================================================================================");
        for (JobInstance h : jqmClient.newQuery().invoke())
        {
            jqmlogger.debug("JobInstance Id: " + h.getId() + " | " + h.getState() + " | JD: " + h.getApplicationName() + " | "
                    + h.getQueueName() + " | enqueue: " + format.format(h.getEnqueueDate().getTime()) + " | exec: "
                    + (h.getBeganRunningDate() != null ? format.format(h.getBeganRunningDate().getTime()) : null) + " | end: "
                    + (h.getEndDate() != null ? format.format(h.getEndDate().getTime()) : null));
        }
        jqmlogger.debug("==========================================================================================");
    }

    protected void displayAllQueueTable()
    {
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("HH:mm:ss.SSS");
        jqmlogger.debug("==========================================================================================");
        for (JobInstance h : jqmClient.newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).invoke())
        {
            jqmlogger.debug("JobInstance Id: " + h.getId() + " | " + h.getState() + " | JD: " + h.getApplicationName() + " | "
                    + h.getQueueName() + " | enqueue: " + format.format(h.getEnqueueDate().getTime()) + " | exec: "
                    + (h.getBeganRunningDate() != null ? format.format(h.getBeganRunningDate().getTime()) : null) + " | position: "
                    + h.getPosition());
        }
        jqmlogger.debug("==========================================================================================");
    }

    /**
     * This test simply tests pax exam loads.
     */
    @Test
    public void testContainerStarts()
    {
        Assert.isTrue(true);
    }
}
