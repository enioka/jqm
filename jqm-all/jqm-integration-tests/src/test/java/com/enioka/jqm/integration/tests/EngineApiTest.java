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

import java.util.Calendar;
import java.util.List;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.client.api.State;

import org.junit.Assert;
import org.junit.Test;

public class EngineApiTest extends JqmBaseTest
{
    @Test
    public void testSendMsg() throws Exception
    {
        boolean success = false;
        boolean success2 = false;
        boolean success3 = false;

        int i = JqmSimpleTest.create(cnx, "pyl.EngineApiSend3Msg").run(this);

        List<String> messages = jqmClient.newQuery().setJobInstanceId(i).invoke().get(0).getMessages();
        for (String msg : messages)
        {
            if (msg.equals("Les marsus sont nos amis, il faut les aimer aussi!"))
            {
                success = true;
            }
            if (msg.equals("Les marsus sont nos amis, il faut les aimer aussi!2"))
            {
                success2 = true;
            }
            if (msg.equals("Les marsus sont nos amis, il faut les aimer aussi!3"))
            {
                success3 = true;
            }
        }

        Assert.assertEquals(true, success);
        Assert.assertEquals(true, success2);
        Assert.assertEquals(true, success3);
    }

    @Test
    public void testSendProgress() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiProgress").addWaitMargin(10000).run(this);

        List<JobInstance> res = jqmClient.newQuery().invoke();
        Assert.assertEquals((Integer) 50, res.get(0).getProgress());
    }

    /**
     * To stop a job, just throw an exception
     */
    @Test
    public void testThrowable() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.SecThrow").expectOk(0).expectNonOk(1).run(this);
    }

    /**
     * A parent job can wait for all its children - then its end date should be after the end date of the children.
     */
    @Test
    public void testWaitChildren() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiWaitAll").expectOk(6).run(this);

        List<JobInstance> jj = jqmClient.newQuery().addSortAsc(Sort.ID).addStatusFilter(State.ENDED).invoke();
        Calendar parentEnd = jj.get(0).getEndDate();
        for (int i = 1; i < 6; i++)
        {
            Assert.assertTrue(parentEnd.after(jj.get(i).getEndDate()));
        }
    }

    @Test
    public void testGetChildrenStatus() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.EngineApiGetStatus").expectNonOk(1).expectOk(2).run(this);
    }
}
