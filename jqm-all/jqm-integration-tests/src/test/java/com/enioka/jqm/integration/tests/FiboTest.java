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

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.test.helpers.TestHelpers;

public class FiboTest extends JqmBaseTest
{
    @Before
    public void b()
    {
        TestHelpers.setNodesLogLevel("INFO", cnx);
    }

    @After
    public void a()
    {
        // CommonService.setLogLevel("DEBUG");
    }

    @Test
    public void testFibo() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.StressFibo").addRuntimeParameter("p1", "1").addRuntimeParameter("p2", "2").addWaitMargin(20000)
                .expectOk(11).run(this);
        // 1: (1,2) - 2: (2,3) - 3: (3,5) - 4: (5,8) - 5: (8,13) - 6: (13,21) - 7:
        // (21,34) - 8: (34,55) - 9: (55,89) - 10: (89,144) -
        // 11: (134,233)
    }

    @Test
    public void testEnqueueSynchronously() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.StressFiboSync").addRuntimeParameter("p1", "34").addRuntimeParameter("p2", "55").expectOk(4)
                .run(this);

        List<JobInstance> res = jqmClient.newQuery().addSortAsc(Sort.ID).invoke();
        JobInstance h1, h2 = null;
        for (JobInstance h : res)
        {
            h1 = h2;
            h2 = h;
            if (h1 == null)
            {
                continue;
            }
            Assert.assertEquals(h2.getParent(), h1.getId());
            Assert.assertTrue(h2.getEndDate().compareTo(h1.getEndDate()) <= 0);
            Assert.assertTrue(h2.getEndDate().compareTo(h1.getBeganRunningDate()) > 0);
        }
    }

    @Test
    public void testFiboHib() throws Exception
    {
        AssumeHsqldb();
        JqmSimpleTest.create(cnx, "pyl.StressFiboHib", "jqm-test-pyl-hibapi").addRuntimeParameter("p1", "1").addRuntimeParameter("p2", "2")
                .addWaitMargin(60000).expectOk(11).run(this);
    }
}
