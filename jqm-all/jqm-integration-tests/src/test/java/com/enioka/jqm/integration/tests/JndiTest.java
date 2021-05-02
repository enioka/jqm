/**
 * Copyright © 2013 enioka. All rights reserved
 * Authors: Marc-Antoine GOUILLART (marc-antoine.gouillart@enioka.com)
 *          Pierre COPPEE (pierre.coppee@enioka.com)
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

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.naming.spi.NamingManager;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.State;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class JndiTest extends JqmBaseTest
{
    // @Test
    // NOT AN AUTO TEST: this requires to have MQ Series jars which are not libre
    // software!
    public void testJmsWmq() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.testpackages.SuperTestPayload", null,
                "jqm-tests/jqm-test-jndijms-wmq/target/test.jar", TestHelpers.qVip, 42, "Jms", "Franquin", "ModuleMachin", "other1",
                "other2", "other3", false, cnx);
        jqmClient.newJobRequest("Jms", "TestUser").addParameter("p1", "1").addParameter("p2", "2").enqueue();

        // Create JMS JNDI references for use by the test jar
        CreationTools.createJndiQueueMQSeries(cnx, "jms/testqueue", "test Queue", "Q.GEO.OUT", null);
        CreationTools.createJndiQcfMQSeries(cnx, "jms/qcf", "test QCF", "10.0.1.90", "QM.TEC1", 1414, "WASCHANNEL");

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx)); // Exception in jar => CRASHED
    }

    @Test
    public void testJmsAmq() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.testpackages.SuperTestPayload", null,
                "jqm-tests/jqm-test-jndijms-amq/target/test.jar", TestHelpers.qVip, 42, "Jms", null, "Franquin", "ModuleMachin", "other1",
                "other2", false, cnx);
        jqmClient.newJobRequest("Jms", "TestUser").enqueue();

        // Create JMS JNDI references for use by the test jar
        CreationTools.createJndiQueueActiveMQ(cnx, "jms/testqueue", "test queue", "Q.TEST", null);
        CreationTools.createJndiQcfActiveMQ(cnx, "jms/qcf", "test QCF", "vm:broker:(tcp://localhost:1234)?persistent=false&useJmx=false",
                null);
        cnx.commit();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx)); // Exception in jar => CRASHED
    }

    @Test
    public void testJmsAmqWrongAlias() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.testpackages.SuperTestPayload", null,
                "jqm-tests/jqm-test-jndijms-amq/target/test.jar", TestHelpers.qVip, 42, "Jms", null, "Franquin", "ModuleMachin", "other1",
                "other2", false, cnx);
        jqmClient.newJobRequest("Jms", "TestUser").enqueue();

        // Create JMS JNDI references for use by the test jar
        CreationTools.createJndiQueueActiveMQ(cnx, "jms/testqueue", "test queue", "Q.TEST", null);
        CreationTools.createJndiQcfActiveMQ(cnx, "jms/qcf2", "test QCF", "vm:broker:(tcp://localhost:1234)?persistent=false&useJmx=false",
                null);
        cnx.commit();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
        Assert.assertEquals(State.CRASHED, jqmClient.newQuery().invoke().get(0).getState());
    }

    @Test
    public void testDefCon() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiDefCon").run(this);
    }

    @Test
    public void testFileJndi() throws Exception
    {
        // Create JMS JNDI references for use by the test jar
        String path = "./testdir";
        CreationTools.createJndiFile(cnx, "fs/testdirectory", "test directory", path);
        cnx.commit();

        // Create the directory...
        (new File(path)).mkdir();

        // Test
        try
        {
            File f = (File) NamingManager.getInitialContext(null).lookup("fs/testdirectory");
            Assert.assertTrue(f.isDirectory());
            f.delete();
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testUrlJndi() throws Exception
    {
        // Create JMS JNDI references for use by the test jar
        String url = "http://www.marsupilami.com";
        CreationTools.createJndiUrl(cnx, "url/testurl", "test directory", url);
        cnx.commit();

        try
        {
            URL f = (URL) NamingManager.getInitialContext(null).lookup("url/testurl");
            Assert.assertEquals(url, f.toString());
        }
        catch (Exception e)
        {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testJndiFile() throws Exception
    {
        // Create JMS JNDI references for use by the test jar
        CreationTools.createJndiFile(cnx, "fs/test", "test resource", "/tmp");

        JqmSimpleTest.create(cnx, "pyl.JndiFile", "jqm-test-pyl-nodep").run(this);
    }

    @Test
    public void testJndiServerName() throws Exception
    {
        addAndStartEngine();

        String s = (String) InitialContext.doLookup("serverName");
        Assert.assertEquals("localhost", s);
    }

    @Test
    public void testJndiJdbcPool() throws Exception
    {
        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        cnx.runUpdate("jndiprm_update_value_by_key", "true", "jmxEnabled");
        cnx.commit();

        addAndStartEngine();

        CreationTools.createJobDef(null, true, "pyl.JndiDb", null, "jqm-tests/jqm-test-pyl-nodep/target/test.jar", TestHelpers.qVip, 42,
                "TestApp", null, "Test", "ModuleTest", "other", "other", false, cnx);
        jqmClient.newJobRequest("TestApp", "TestUser").enqueue();
        TestHelpers.waitFor(1, 5000, cnx);

        TestHelpers.testOkCount(1, cnx);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int nb = (Integer) mbs.getAttribute(new ObjectName("com.enioka.jqm:type=JdbcPool,name=jdbc/test"), "Active");
        Assert.assertEquals(0, nb);
    }

    @Test
    public void testJndiJdbcPoolLeakWithoutHunter() throws Exception
    {
        // Sanity check - our test DOES leak connections
        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        cnx.runUpdate("jndiprm_update_value_by_key", "true", "jmxEnabled");
        cnx.commit();

        addAndStartEngine();

        // Run the payload, it should leave an open conn at the end
        CreationTools.createJobDef(null, true, "pyl.JndiDbLeak", null, "jqm-tests/jqm-test-pyl-nodep/target/test.jar", TestHelpers.qVip, 42,
                "TestApp", null, "Test", "ModuleTest", "other", "other", false, cnx);
        jqmClient.newJobRequest("TestApp", "TestUser").enqueue();
        TestHelpers.waitFor(1, 5000, cnx);

        TestHelpers.testOkCount(1, cnx);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int nb = (Integer) mbs.getAttribute(new ObjectName("com.enioka.jqm:type=JdbcPool,name=jdbc/test"), "Active");
        Assert.assertEquals(1, nb);

        // Clean the pool forcefully so as not to impact other tests
        // JndiContext.createJndiContext().resetSingletons();
    }

    @Test
    public void testJndiJdbcPoolLeakWithHunter() throws Exception
    {
        // Create a connection with our custom interceptor
        Map<String, String> prms = new HashMap<>(1);
        prms.put("jdbcInterceptors", "com.enioka.jqm.providers.PayloadInterceptor");
        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", prms);

        cnx.runUpdate("jndiprm_update_value_by_key", "true", "jmxEnabled");
        cnx.commit();

        addAndStartEngine();

        // Sanity check: the leak hunter does not harm normal payloads.
        CreationTools.createJobDef(null, true, "pyl.JndiDb", null, "jqm-tests/jqm-test-pyl-nodep/target/test.jar", TestHelpers.qVip, 42,
                "TestApp", null, "Test", "ModuleTest", "other", "other", false, cnx);
        jqmClient.newJobRequest("TestApp", "TestUser").enqueue();
        TestHelpers.waitFor(1, 5000, cnx);
        TestHelpers.testOkCount(1, cnx);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        int nb = (Integer) mbs.getAttribute(new ObjectName("com.enioka.jqm:type=JdbcPool,name=jdbc/test"), "Active");
        Assert.assertEquals(0, nb);

        // Run the payload, it should leave an open conn that should be forcibly closed.
        CreationTools.createJobDef(null, true, "pyl.JndiDbLeak", null, "jqm-tests/jqm-test-pyl-nodep/target/test.jar", TestHelpers.qVip, 42,
                "TestApp2", null, "Test", "ModuleTest", "other", "other", false, cnx);
        jqmClient.newJobRequest("TestApp2", "TestUser").enqueue();
        TestHelpers.waitFor(1, 5000, cnx);

        // Test
        TestHelpers.testOkCount(2, cnx);
        nb = (Integer) mbs.getAttribute(new ObjectName("com.enioka.jqm:type=JdbcPool,name=jdbc/test"), "Active");
        Assert.assertEquals(0, nb);
    }
}
