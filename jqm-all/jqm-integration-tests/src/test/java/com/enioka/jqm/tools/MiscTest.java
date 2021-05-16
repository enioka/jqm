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
package com.enioka.jqm.tools;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;

import com.enioka.api.admin.QueueDto;
import com.enioka.jqm.model.Queue;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.JndiObjectResourceDto;
import com.enioka.jqm.api.JobRequest;
import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.api.Query;
import com.enioka.jqm.model.JobDef.PathType;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class MiscTest extends JqmBaseTest
{
    @Test
    public void testEmail() throws Exception
    {
        // Do not run in Eclipse, as it does not support the SMTP Maven plugin.
        Assume.assumeTrue(!System.getProperty("java.class.path").contains("eclipse"));

        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        JobRequest.create("MarsuApplication", "TestUser").setEmail("test@jqm.com").submit();

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx); // Need time for async mail sending.

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));

        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        int nbMail = 0;
        try
        {
            Session session = Session.getInstance(props, null);
            Store store = session.getStore();
            store.connect("localhost", 10143, "testlogin", "testpassword");
            Folder inbox = store.getFolder("INBOX");
            nbMail = inbox.getMessageCount();
        }
        catch (Exception mex)
        {
            mex.printStackTrace();
        }
        Assert.assertEquals(1, nbMail);
    }

    @Test
    public void testLongName() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "Marsu-Application-nnnnnnnn-nnnnnn-nnnnnnnnnn-nnNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNnn-nnnnnnnnn", null, "Franquin",
                "ModuleMachin", "other", "other", true, cnx);

        JobRequest j = new JobRequest("Marsu-Application-nnnnnnnn-nnnnnn-nnnnnnnnnn-nnNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNNnn-nnnnnnnnn",
                "TestUser");
        JqmClientFactory.getClient().enqueue(j);

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testJobWithSystemExit() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.SecExit", "jqm-test-pyl-nodep").expectOk(0).expectNonOk(1).run(this);
    }

    @Test
    public void testJobWithPersistenceUnit() throws Exception
    {
        // The PU test expects an HSQLDB database which does not exist when running the tests on other databases
        Assume.assumeTrue(JqmBaseTest.s != null);

        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        CreationTools.createDatabaseProp("jdbc/jqm2", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:hsql://localhost/testdbengine", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);

        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-em/target/test.jar", TestHelpers.qVip, 42, "jqm-test-em",
                null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        JobRequest.create("jqm-test-em", "TestUser").submit();

        addAndStartEngine();
        TestHelpers.waitFor(2, 10000, cnx);

        Assert.assertEquals(2, TestHelpers.getOkCount(cnx));
    }

    @Test
    public void testJobWithPersistenceUnitAndEngineApi() throws Exception
    {
        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        JqmSimpleTest.create(cnx, "pyl.CompatHibApi", "jqm-test-pyl-hibapi").expectOk(2).run(this);
    }

    @Test
    public void testJobWithPersistenceUnitAndEngineApiAndXmlParams() throws Exception
    {
        CreationTools.createDatabaseProp("jdbc/test", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdbmarsu", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        CreationTools.createDatabaseProp("jdbc/jqm2", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:hsql://localhost/testdbengine", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);
        cnx.commit();

        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmlstop.xml", cnx);
        cnx.commit();

        JobRequest j = new JobRequest("CompatHibApi", "TestUser");
        JqmClientFactory.getClient().enqueue(j);

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
    }

    @Test
    public void testRemoteStop() throws Exception
    {
        Helpers.setSingleParam("internalPollingPeriodMs", "10", cnx);

        addAndStartEngine();

        cnx.runUpdate("node_update_stop_by_id", TestHelpers.node.getId());
        cnx.commit();

        TestHelpers.waitFor(2, 3000, cnx);
        Assert.assertFalse(engines.get("localhost").areAllPollersPolling());
        engines.clear();
    }

    @Test
    public void testNoDoubleStart() throws Exception
    {
        Helpers.setSingleParam("internalPollingPeriodMs", "60000", cnx);
        Helpers.setSingleParam("disableVerboseStartup", "false", cnx);

        JqmEngine engine1 = new JqmEngine();
        engine1.start("localhost", null);

        JqmEngine engine2 = new JqmEngine();
        try
        {
            engine2.start("localhost", null);
            Assert.fail("engine should not have been able to start");
        }
        catch (JqmInitErrorTooSoon e)
        {
            jqmlogger.info("", e);
        }
        finally
        {
            engine1.stop();
        }
    }

    @Test
    public void testStartupCleanupRunning() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-em/target/test.jar", TestHelpers.qVip, 42, "jqm-test-em",
                null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        /// Create a running job that should be cleaned at startup
        int i1 = JqmClientFactory.getClient().enqueue("jqm-test-em", "test");
        cnx.runUpdate("ji_update_status_by_id", TestHelpers.node.getId(), i1);
        cnx.runUpdate("jj_update_run_by_id", i1);
        cnx.commit();

        addAndStartEngine();

        Assert.assertEquals(0, TestHelpers.getQueueAllCount(cnx));
        Assert.assertEquals(1, TestHelpers.getHistoryAllCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testStartupCleanupAttr() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-em/target/test.jar", TestHelpers.qVip, 42, "jqm-test-em",
                null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        // Create a running job that should be cleaned at startup
        int i = JqmClientFactory.getClient().enqueue("jqm-test-em", "test");
        cnx.runUpdate("ji_update_status_by_id", TestHelpers.node.getId(), i);
        cnx.commit();

        addAndStartEngine();

        Assert.assertEquals(0, TestHelpers.getQueueAllCount(cnx));
        Assert.assertEquals(1, TestHelpers.getHistoryAllCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testQuery() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.KillMe", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qNormal, 42,
                "jqm-test-kill", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        cnx.commit();

        JqmClientFactory.getClient().enqueue("jqm-test-kill", "test");
        JqmClientFactory.getClient().enqueue("jqm-test-kill", "test");
        JqmClientFactory.getClient().enqueue("jqm-test-kill", "test");
        JqmClientFactory.getClient().enqueue("jqm-test-kill", "test");
        JqmClientFactory.getClient().enqueue("jqm-test-kill", "test");

        jqmlogger.debug("COUNT RUNNING " + cnx.runSelectSingle("ji_select_count_running", Integer.class));
        jqmlogger.debug("COUNT ALL     " + cnx.runSelectSingle("ji_select_count_all", Integer.class));
        Assert.assertEquals(0, Query.create().setQueryLiveInstances(true).setQueryHistoryInstances(false)
                .addStatusFilter(com.enioka.jqm.api.State.RUNNING).addStatusFilter(com.enioka.jqm.api.State.ENDED).run().size());
        Assert.assertEquals(5, Query.create().setQueryLiveInstances(true).setQueryHistoryInstances(false)
                .addStatusFilter(com.enioka.jqm.api.State.SUBMITTED).run().size());
    }

    @Test
    public void testMultiLog() throws Exception
    {
        PrintStream out_ini = System.out;
        PrintStream err_ini = System.err;

        Helpers.setSingleParam("logFilePerLaunch", "true", cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        int i = JobRequest.create("MarsuApplication", "TestUser").submit();
        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        String fileName = StringUtils.leftPad("" + i, 10, "0") + ".stdout.log";
        File f = new File(FilenameUtils.concat(((MultiplexPrintStream) System.out).rootLogDir, fileName));

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
        Assert.assertTrue(f.exists());

        System.setErr(err_ini);
        System.setOut(out_ini);
    }

    @Test
    public void testMavenArtifact()
    {
        CreationTools.createJobDef(null, true, "pyl.Nothing", null, "com.enioka.jqm:jqm-test-pyl-nodep:1.3.2", TestHelpers.qVip, 42,
                "jqm-test-maven", null, "Franquin", "ModuleMachin", "other", "other", false, cnx, null, false, null, false, PathType.MAVEN);
        JobRequest.create("jqm-test-maven", null).submit();
        addAndStartEngine();

        TestHelpers.waitFor(1, 10000, cnx);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testMetaJndiBug()
    {
        JndiObjectResourceDto or = new JndiObjectResourceDto();
        or.setAuth("CONTAINER");
        or.setDescription("description");
        or.setFactory("my.factory");
        or.setName("jndi/resource");
        or.setSingleton(true);
        or.setType("classname");

        or.addParameter("prm1", "val1");

        MetaService.upsertJndiObjectResource(cnx, or);
        JndiObjectResourceDto tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val1", tmp.getParameters().get("prm1"));

        tmp.addParameter("prm1", "val2");
        MetaService.upsertJndiObjectResource(cnx, tmp);
        tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val2", tmp.getParameters().get("prm1"));

        // Now sync.
        List<JndiObjectResourceDto> dtos = new ArrayList<>();
        dtos.add(tmp);
        MetaService.syncJndiObjectResource(cnx, dtos);
        tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val2", tmp.getParameters().get("prm1"));

        dtos.get(0).addParameter("prm1", "val3");
        MetaService.syncJndiObjectResource(cnx, dtos);
        tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val3", tmp.getParameters().get("prm1"));

        dtos.get(0).addParameter("prm2", "val1");
        MetaService.syncJndiObjectResource(cnx, dtos);
        tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val3", tmp.getParameters().get("prm1"));
        Assert.assertEquals("val1", tmp.getParameters().get("prm2"));

        dtos.get(0).removeParameter("prm2");
        MetaService.syncJndiObjectResource(cnx, dtos);
        tmp = MetaService.getJndiObjectResource(cnx, "jndi/resource");
        Assert.assertEquals("val3", tmp.getParameters().get("prm1"));
        Assert.assertEquals(1, tmp.getParameters().size());

        dtos.clear();
        MetaService.syncJndiObjectResource(cnx, dtos);
        Assert.assertEquals(0, MetaService.getJndiObjectResource(cnx).size());

        cnx.commit();
    }

    @Test
    public void testMultipleDefaultQueue()
    {
        // Add two default queue
        QueueDto q1 = new QueueDto();
        q1.setName("AnotherDefaultQueue");
        q1.setDescription("default");
        q1.setDefaultQueue(true);

        Assert.assertTrue(q1.isDefaultQueue());
        MetaService.upsertQueue(cnx, q1);

        // Should not explode
        Assert.assertNotNull(TestHelpers.getDefaultQueueId(cnx));


        List<QueueDto> queueDtoList = new ArrayList<>();
        queueDtoList = MetaService.getQueues(cnx);

        QueueDto q2 = new QueueDto();
        q2.setName("YanDeQ");
        q2.setDescription("Yet Another Default Queue");
        q2.setDefaultQueue(true);
        queueDtoList.add(q2);

        Assert.assertTrue(q2.isDefaultQueue());

        MetaService.syncQueues(cnx, queueDtoList);

        // Should not explode
        Assert.assertNotNull(TestHelpers.getDefaultQueueId(cnx));
    }
}
