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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.NodeDto;
import com.enioka.api.admin.QueueDto;
import com.enioka.api.admin.QueueMappingDto;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class XmlTest extends JqmBaseTest
{
    @Test
    public void testExportQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.tests.App", null, "jqm-tests/jqm-test-fibo/target/test.jar",
                TestHelpers.qVip, 42, "Fibo", null, "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-geo/target/test.jar", TestHelpers.qVip, 42, "Geo", null,
                "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qNormal, 42,
                "DateTime", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        ArrayList<String> tmp = new ArrayList<>();
        tmp.add("VIPQueue");
        tmp.add("NormalQueue");

        XmlQueueExporter.export(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml", cnx, tmp);

        File t = new File(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml");
        Assert.assertEquals(true, t.exists());

        // --> Test Import
        XmlQueueParser.parse(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml", cnx);

        try
        {
            Queue.select(cnx, "q_select_by_key", "VIPQueue");
            Queue.select(cnx, "q_select_by_key", "NormalQueue");

            JobDef jd1 = JobDef.select_key(cnx, "Fibo");
            JobDef jd2 = JobDef.select_key(cnx, "Geo");
            JobDef jd3 = JobDef.select_key(cnx, "DateTime");

            Assert.assertEquals("VIPQueue", jd1.getQueue(cnx).getName());
            Assert.assertEquals("VIPQueue", jd2.getQueue(cnx).getName());
            Assert.assertEquals("NormalQueue", jd3.getQueue(cnx).getName());
        }
        catch (NoResultException e)
        {
            Assert.fail("missing configuration element");
        }

        t.delete();
    }

    @Test
    public void testExportQueueAll() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.tests.App", null, "jqm-tests/jqm-test-fibo/target/test.jar",
                TestHelpers.qVip, 42, "Fibo", null, "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-geo/target/test.jar", TestHelpers.qVip, 42, "Geo", null,
                "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qNormal, 42,
                "DateTime", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        XmlQueueExporter.export(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml", cnx);

        File t = new File(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml");
        Assert.assertEquals(true, t.exists());

        // --> Test Import
        XmlQueueParser.parse(TestHelpers.node.getDlRepo() + "xmlexportqueuetest.xml", cnx);

        try
        {
            Queue.select(cnx, "q_select_by_key", "VIPQueue");
            Queue.select(cnx, "q_select_by_key", "NormalQueue");

            JobDef jd1 = JobDef.select_key(cnx, "Fibo");
            JobDef jd2 = JobDef.select_key(cnx, "Geo");
            JobDef jd3 = JobDef.select_key(cnx, "DateTime");

            Assert.assertEquals("VIPQueue", jd1.getQueue(cnx).getName());
            Assert.assertEquals("VIPQueue", jd2.getQueue(cnx).getName());
            Assert.assertEquals("NormalQueue", jd3.getQueue(cnx).getName());
        }
        catch (NoResultException e)
        {
            Assert.fail("missing configuration element");
        }

        t.delete();
    }

    @Test
    public void testXmlParser()
    {
        // Init the default queue (don't start the engine!)
        Helpers.updateConfiguration(cnx);

        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltest.xml", cnx);
        cnx.commit();

        List<JobDef> jd = JobDef.select(cnx, "jd_select_all");
        JobDef fibo = JobDef.select_key(cnx, "Fibo");

        Assert.assertEquals(2, jd.size());
        Assert.assertEquals("Fibo", fibo.getApplicationName());
        Assert.assertEquals(true, fibo.isCanBeRestarted());
        Assert.assertEquals("com.enioka.jqm.tests.App", fibo.getJavaClassName());
        Assert.assertEquals(TestHelpers.qVip, fibo.getQueue());
        Assert.assertEquals("ApplicationTest", fibo.getApplication());
        Assert.assertEquals("TestModuleRATONLAVEUR", fibo.getModule());
        Assert.assertEquals(false, fibo.isHighlander());
        Assert.assertEquals("1", fibo.getParametersMap(cnx).get("p1"));
        Assert.assertEquals("2", fibo.getParametersMap(cnx).get("p2"));
        Assert.assertEquals("com.enioka.jqm.tests.App", fibo.getJavaClassName());
        Assert.assertEquals("com.enioka.jqm.tests.App", fibo.getJavaClassName());
    }

    @Test
    public void testUpdateJobDef()
    {
        // Init the default queue (don't start the engine!)
        Helpers.updateConfiguration(cnx);

        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltest.xml", cnx);
        cnx.commit();

        // Sanity check
        List<JobDef> jd = JobDef.select(cnx, "jd_select_all");
        JobDef fibo = JobDef.select_key(cnx, "Fibo");

        Assert.assertEquals(2, jd.size());
        Assert.assertEquals("Fibo", fibo.getApplicationName());
        Assert.assertEquals("vdjvkdv", fibo.getKeyword1());
        Assert.assertEquals("sgfbgg", fibo.getKeyword2());
        Assert.assertEquals("jvhkdfl", fibo.getKeyword3());

        // Import and therefore update the job definitions.
        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltest_update.xml", cnx);
        cnx.commit();

        jd = JobDef.select(cnx, "jd_select_all");
        fibo = JobDef.select_key(cnx, "Fibo");

        Assert.assertEquals(2, jd.size());
        Assert.assertEquals("Fibo", fibo.getApplicationName());
        Assert.assertEquals("NEWVALUE", fibo.getKeyword1());
        Assert.assertEquals("", fibo.getKeyword2() == null ? "" : fibo.getKeyword2());
        Assert.assertEquals(null, fibo.getKeyword3());
    }

    @Test
    public void testExportJobDef() throws Exception
    {
        Map<String, String> jdp = new HashMap<>();
        jdp.put("test-key", "test-value");
        CreationTools.createJobDef("My Description", true, "com.enioka.jqm.tests.App", jdp, "jqm-tests/jqm-test-fibo/target/test.jar",
                TestHelpers.qVip, 42, "Fibo", "App", "ModuleMachin", "other1", "other2", null, false, cnx, "Isolation", true, "HIDDEN");
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-geo/target/test.jar", TestHelpers.qVip, 42, "Geo", null,
                "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qNormal, 42,
                "DateTime", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qNormal, 42,
                "DateTime2", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        XmlJobDefExporter.export(TestHelpers.node.getDlRepo() + "xmlexportjobdeftest.xml", cnx);

        File f = new File(TestHelpers.node.getDlRepo() + "xmlexportjobdeftest.xml");
        Assert.assertEquals(true, f.exists());

        // -> Delete all entries and try to reimport them from the exported file
        TestHelpers.cleanup(cnx, true);

        XmlJobDefParser.parse(TestHelpers.node.getDlRepo() + "xmlexportjobdeftest.xml", cnx);

        // test the 4 JobDef were imported
        JobDef fibo = null;
        try
        {
            fibo = JobDef.select_key(cnx, "Fibo");
            JobDef.select_key(cnx, "Geo");
            JobDef.select_key(cnx, "DateTime");
            JobDef.select_key(cnx, "DateTime2");
        }
        catch (NoResultException e)
        {
            Assert.fail("missing configuration element");
        }

        Assert.assertEquals("My Description", fibo.getDescription());
        Assert.assertEquals("App", fibo.getApplication());
        Assert.assertEquals("jqm-tests/jqm-test-fibo/target/test.jar", fibo.getJarPath());
        Assert.assertEquals("FS", fibo.getPathType().toString());
        Assert.assertEquals("VIPQueue", fibo.getQueue(cnx).getName());
        Assert.assertEquals(true, fibo.isCanBeRestarted());
        Assert.assertEquals("com.enioka.jqm.tests.App", fibo.getJavaClassName());
        Assert.assertEquals("ModuleMachin", fibo.getModule());
        Assert.assertEquals("other1", fibo.getKeyword1());
        Assert.assertEquals("other2", fibo.getKeyword2());
        Assert.assertEquals(null, fibo.getKeyword3());
        Assert.assertEquals(false, fibo.isHighlander());
        Assert.assertEquals("Isolation", fibo.getClassLoader(cnx).getName());
        Assert.assertEquals(true, fibo.getClassLoader().isChildFirst());
        Assert.assertEquals("HIDDEN", fibo.getClassLoader().getHiddenClasses());

        f.delete();
    }

    @Test
    public void testImportThenReimportJobDefWithPrms()
    {
        // Init the default queue (don't start the engine!)
        Helpers.updateConfiguration(cnx);

        // First import
        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltest.xml", cnx);
        cnx.commit();

        List<JobDef> jd = JobDef.select(cnx, "jd_select_all");
        Assert.assertEquals(2, jd.size());
        JobDef fibo = JobDef.select_key(cnx, "Fibo");
        Assert.assertEquals("Fibo", fibo.getApplicationName());
        Assert.assertEquals("1", fibo.getParametersMap(cnx).get("p1"));

        // Second import - parameters are different, note 3 instead of 1
        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltest_np.xml", cnx);
        cnx.commit();

        jd = JobDef.select(cnx, "jd_select_all");
        fibo = JobDef.select_key(cnx, "Fibo");
        Assert.assertEquals(2, jd.size());
        Assert.assertNotNull(fibo);
        Assert.assertEquals("Fibo", fibo.getApplicationName());
        Assert.assertEquals("3", fibo.getParametersMap(cnx).get("p1"));
    }

    @Test
    public void testImportJobdefWithQueue()
    {
        // Init the default queue (don't start the engine!)
        Helpers.updateConfiguration(cnx);

        XmlJobDefParser.parse("target/payloads/jqm-test-xml/xmltestnewqueue.xml", cnx);
        cnx.commit();

        List<JobDef> jd = JobDef.select(cnx, "jd_select_all");
        Assert.assertEquals(2, jd.size());

        // Was the queue created (and only once)?
        Queue q = Queue.select_key(cnx, "NewQueue");
        Assert.assertEquals("Created from a jobdef import. Description should be set later", q.getDescription());
        cnx.close();
    }

    @Test
    public void testImportQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "com.enioka.jqm.tests.App", null, "jqm-tests/jqm-test-fibo/target/test.jar",
                TestHelpers.qVip, 42, "Fibo", null, "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-geo/target/test.jar", TestHelpers.qVip, 42, "Geo", null,
                "Franquin", "ModuleMachin", "other1", "other2", false, cnx);
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qNormal, 42,
                "DateTime", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        XmlQueueParser.parse("target/payloads/jqm-test-xml/xmlqueuetest.xml", cnx);

        try
        {
            Queue.select(cnx, "q_select_by_key", "XmlQueue");
            Queue.select(cnx, "q_select_by_key", "XmlQueue2");

            JobDef jd1 = JobDef.select_key(cnx, "Fibo");
            JobDef jd2 = JobDef.select_key(cnx, "Geo");
            JobDef jd3 = JobDef.select_key(cnx, "DateTime");

            Assert.assertEquals("XmlQueue", jd1.getQueue(cnx).getName());
            Assert.assertEquals("XmlQueue", jd2.getQueue(cnx).getName());
            Assert.assertEquals("XmlQueue2", jd3.getQueue(cnx).getName());
        }
        catch (NoResultException e)
        {
            Assert.fail("missing configuration element");
        }
    }

    @Test
    public void testImportConfiguration()
    {
        // First import = creation
        XmlConfigurationParser.parse("target/payloads/jqm-test-xml/xmlnodeimport1.xml", cnx);
        cnx.commit();

        List<NodeDto> nodes = MetaService.getNodes(cnx);
        NodeDto node = null;

        for (NodeDto dto : nodes)
        {
            if (dto.getName().equals("Node1"))
            {
                node = dto;
                break;
            }
        }

        Assert.assertNotNull(node);

        Assert.assertEquals("Node1", node.getName());

        Assert.assertEquals("localhost", node.getDns());
        Assert.assertEquals((Integer) 1789, node.getPort());
        Assert.assertEquals((Integer) 1790, node.getJmxRegistryPort());
        Assert.assertEquals((Integer) 1791, node.getJmxServerPort());

        Assert.assertTrue(node.getEnabled());
        Assert.assertTrue(node.getLoadApiAdmin());
        Assert.assertTrue(node.getLoadApiClient());
        Assert.assertTrue(node.getLoapApiSimple());

        Assert.assertEquals("./jobs", node.getJobRepoDirectory());
        Assert.assertEquals("./tmp", node.getTmpDirectory());
        Assert.assertEquals("./outputfiles", node.getOutputDirectory());

        Assert.assertEquals("INFO", node.getRootLogLevel());

        Assert.assertEquals("value1", GlobalParameter.getParameter(cnx, "key1", "none"));

        QueueMappingDto queueMapping = null;
        for (QueueMappingDto qm : MetaService.getQueueMappings(cnx))
        {
            if (qm.getNodeName().equals("Node1"))
            {
                queueMapping = qm;
                break;
            }
        }
        Assert.assertNotNull(queueMapping);
        Assert.assertEquals((Integer) 2000, queueMapping.getPollingInterval());

        QueueDto q = MetaService.getQueue(cnx, queueMapping.getQueueId());
        Assert.assertTrue(q.isDefaultQueue());

        Assert.assertEquals("test1", MetaService.getJndiObjectResource(cnx, "string/test1").getParameters().get("STRING"));

        // 2nd import (other file) = update
        XmlConfigurationParser.parse("target/payloads/jqm-test-xml/xmlnodeimport2.xml", cnx);
        cnx.commit();

        nodes = MetaService.getNodes(cnx);
        node = null;
        for (NodeDto dto : nodes)
        {
            if (dto.getName().equals("Node1"))
            {
                node = dto;
                break;
            }
        }

        Assert.assertNotNull(node);

        Assert.assertEquals("Node1", node.getName());

        Assert.assertEquals("localhost2", node.getDns());
        Assert.assertEquals((Integer) 2789, node.getPort());
        Assert.assertEquals((Integer) 2790, node.getJmxRegistryPort());
        Assert.assertEquals((Integer) 2791, node.getJmxServerPort());

        Assert.assertFalse(node.getEnabled());
        Assert.assertFalse(node.getLoadApiAdmin());
        Assert.assertFalse(node.getLoadApiClient());
        Assert.assertFalse(node.getLoapApiSimple());

        Assert.assertEquals("./jobs2", node.getJobRepoDirectory());
        Assert.assertEquals("./tmp2", node.getTmpDirectory());
        Assert.assertEquals("./outputfiles2", node.getOutputDirectory());

        Assert.assertEquals("WARNING", node.getRootLogLevel());

        Assert.assertEquals("value2", GlobalParameter.getParameter(cnx, "key1", "none"));

        queueMapping = null;
        for (QueueMappingDto qm : MetaService.getQueueMappings(cnx))
        {
            if (qm.getNodeName().equals("Node1"))
            {
                queueMapping = qm;
                break;
            }
        }
        Assert.assertNotNull(queueMapping);
        Assert.assertEquals((Integer) 5000, queueMapping.getPollingInterval());

        q = MetaService.getQueue(cnx, queueMapping.getQueueId());
        Assert.assertFalse(q.isDefaultQueue());

        Assert.assertEquals("test1_2", MetaService.getJndiObjectResource(cnx, "string/test1").getParameters().get("STRING"));

        // 3rd import (same file) = stable
        XmlConfigurationParser.parse("target/payloads/jqm-test-xml/xmlnodeimport2.xml", cnx);
        cnx.commit();

        nodes = MetaService.getNodes(cnx);
        node = null;
        for (NodeDto dto : nodes)
        {
            if (dto.getName().equals("Node1"))
            {
                node = dto;
                break;
            }
        }

        Assert.assertNotNull(node);

        Assert.assertEquals("Node1", node.getName());

        Assert.assertEquals("localhost2", node.getDns());
        Assert.assertEquals("WARNING", node.getRootLogLevel());
    }
}
