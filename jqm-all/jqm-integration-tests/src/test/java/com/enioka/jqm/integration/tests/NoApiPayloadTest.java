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

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class NoApiPayloadTest extends JqmBaseTest
{
    @Test
    public void testClassicPayload() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "jqm-test-datetimemaven", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        jqmClient.newJobRequest("jqm-test-datetimemaven", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testRunnable() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.PckRunnable", "jqm-test-pyl-nodep").run(this);
    }

    @Test
    public void testRunnableInject() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiInjectThread").expectOk(3).run(this);

        Assert.assertEquals(3, (int) cnx.runSelectSingle("message_select_count_all", Integer.class));
        Assert.assertEquals(100, (int) jqmClient.newQuery().addSortAsc(Sort.ID).invoke().get(0).getProgress());
    }

    @Test
    public void testMainType() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.PckMain", "jqm-test-pyl-nodep").run(this);
    }

    @Test
    public void testMainTypeInject() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiInject").setSessionId("123X").expectOk(3).run(this);

        Assert.assertEquals(3, (int) cnx.runSelectSingle("message_select_count_all", Integer.class));
        Assert.assertEquals(100, (int) jqmClient.newQuery().invoke().get(0).getProgress());
    }

    @Test
    public void testProvidedApi() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-providedapi/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        // Create an empty lib directory just to be sure no dependencies will be resolved.
        FileUtils.forceMkdir(new File("../jqm-tests/jqm-test-providedapi/target/lib"));

        JobRequest j = jqmClient.newJobRequest("MarsuApplication", "TestUser");
        j.enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testDisabledPayload() throws Exception
    {
        int jd = CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip,
                42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        cnx.runUpdate("jd_update_set_enabled_by_id", false, jd);
        cnx.commit();

        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
        Assert.assertEquals(-1, (int) jqmClient.newQuery().invoke().get(0).getProgress());
    }

    @Test
    public void testMainTypeInjectWithFullApi() throws Exception
    {
        // Here, engine API + full API mix.
        int i = JqmSimpleTest.create(cnx, "pyl.EngineApiInject", "jqm-test-pyl-hibapi").setSessionId("123X").expectOk(3).run(this);

        Assert.assertEquals(1, jqmClient.getJob(i).getMessages().size()); // 1 message per run created by payload
        Assert.assertEquals(100, (int) jqmClient.getJob(i).getProgress());
    }
}
