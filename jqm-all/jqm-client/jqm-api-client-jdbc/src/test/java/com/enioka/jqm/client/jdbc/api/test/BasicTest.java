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
package com.enioka.jqm.client.jdbc.api.test;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.client.api.State;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.DbManager;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.JobDef.PathType;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple tests for checking query syntax (no data). No use of OSGi here, as this lib can be used outside a framework.
 */
public class BasicTest
{
    private static Logger jqmlogger = Logger.getLogger(BasicTest.class);

    @BeforeClass
    public static void beforeClass()
    {
        JqmClientFactory.setProperty("com.enioka.jqm.jdbc.allowSchemaUpdate", "true");
    }

    @Test
    public void testChain()
    {
        // No exception allowed!
        JqmClientFactory.getClient().getQueues();
        jqmlogger.info("q1");
        JqmClientFactory.getClient().getQueues();
        jqmlogger.info("q2");
    }

    @Test
    public void testQuery()
    {
        Query q = JqmClientFactory.getClient().newQuery().setUser("toto");
        q.setInstanceApplication("marsu");
        q.setInstanceKeyword2("pouet");
        q.setInstanceModule("module");
        q.setParentId(12);
        q.setJobInstanceId(132);
        q.setQueryLiveInstances(true);

        q.setJobDefKeyword2("pouet2");

        q.invoke();
    }

    @Test
    public void testQueryDate()
    {
        Query q = JqmClientFactory.getClient().newQuery().setUser("toto");
        q.setInstanceApplication("marsu");
        q.setInstanceKeyword2("pouet");
        q.setInstanceModule("module");
        q.setParentId(12);
        q.setJobInstanceId(132);
        q.setQueryLiveInstances(true);

        q.setEnqueuedBefore(Calendar.getInstance());
        q.setEndedAfter(Calendar.getInstance());
        q.setBeganRunningAfter(Calendar.getInstance());
        q.setBeganRunningBefore(Calendar.getInstance());
        q.setEnqueuedAfter(Calendar.getInstance());
        q.setEnqueuedBefore(Calendar.getInstance());

        q.setJobDefKeyword2("pouet2");

        q.invoke();
    }

    @Test
    public void testQueryStatusOne()
    {
        Query q = JqmClientFactory.getClient().newQuery().setUser("toto");
        q.setQueryLiveInstances(true);
        q.setInstanceApplication("marsu");
        q.addStatusFilter(State.CRASHED);
        q.invoke();
    }

    @Test
    public void testQueryStatusTwo()
    {
        Query q = JqmClientFactory.getClient().newQuery().setUser("toto");
        q.setQueryLiveInstances(true);
        q.setInstanceApplication("marsu");
        q.addStatusFilter(State.CRASHED);
        q.addStatusFilter(State.HOLDED);

        q.invoke();
    }

    @Test
    public void testFluentQuery()
    {
        Query q = JqmClientFactory.getClient().newQuery().setUser("toto");
        q.setQueryLiveInstances(true);
        q.setInstanceApplication("marsu");
        q.addStatusFilter(State.CRASHED);
        q.addStatusFilter(State.HOLDED);

        JqmClientFactory.getClient().newQuery().addStatusFilter(State.RUNNING).setApplicationName("MARSU").invoke();
    }

    @Test
    public void testQueryPercent()
    {
        JqmClientFactory.getClient().newQuery().setApplicationName("%TEST").invoke();
    }

    @Test
    public void testQueryNull()
    {
        // by convention null query is ""...
        JqmClientFactory.getClient().newQuery().setApplicationName("").invoke();
    }

    @Test
    public void testQueueNameId()
    {
        JqmClientFactory.getClient().newQuery().setQueueName("test").invoke();
        JqmClientFactory.getClient().newQuery().setQueueId(12).invoke();
    }

    @Test
    public void testPaginationWithFilter()
    {
        JqmClientFactory.getClient().newQuery().setQueueName("test").setPageSize(10).invoke();
        JqmClientFactory.getClient().newQuery().setQueueId(12).setPageSize(10).invoke();
    }

    @Test
    public void testUsername()
    {
        JqmClientFactory.getClient().newQuery().setUser("test").setPageSize(10).invoke();
    }

    @Test
    public void testSortHistory()
    {
        JqmClientFactory.getClient().newQuery().setUser("test").setPageSize(10).addSortAsc(Sort.APPLICATIONNAME)
                .addSortDesc(Sort.DATEATTRIBUTION).addSortAsc(Sort.DATEEND).addSortDesc(Sort.DATEENQUEUE).addSortAsc(Sort.ID)
                .addSortDesc(Sort.QUEUENAME).addSortAsc(Sort.STATUS).addSortDesc(Sort.USERNAME).addSortAsc(Sort.PARENTID).invoke();
    }

    @Test
    public void testSortJi()
    {
        JqmClientFactory.getClient().newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).setUser("test")
                .addSortAsc(Sort.APPLICATIONNAME).addSortDesc(Sort.DATEATTRIBUTION).addSortDesc(Sort.DATEENQUEUE).addSortAsc(Sort.ID)
                .addSortDesc(Sort.QUEUENAME).addSortAsc(Sort.STATUS).addSortDesc(Sort.USERNAME).addSortAsc(Sort.PARENTID).invoke();
    }

    @Test
    public void testOnlyQueue()
    {
        JqmClientFactory.getClient().newQuery().setQueryLiveInstances(true).setQueryHistoryInstances(false).setUser("test").invoke();
    }

    @Test
    public void testBug159()
    {
        JqmClientFactory.getClient().newQuery().setJobInstanceId(1234).setQueryLiveInstances(true).setQueryHistoryInstances(false)
                .setPageSize(15).setFirstRow(0).invoke();
    }

    @Test
    public void testBug292()
    {
        JqmClientFactory.getClient().newQuery().addSortDesc(Query.Sort.ID).setQueueName("QBATCH").setQueryHistoryInstances(true)
                .setQueryLiveInstances(true).invoke();
    }

    @Test
    public void testBug305()
    {
        Db db = DbManager.getDb();
        try (DbConn cnx = db.getConn())
        {
            int qId = Queue.create(cnx, "q1", "q1 description", true);
            int jobDefdId = JobDef.create(cnx, "test description", "class", null, "jar", qId, 1, "appName", null, null, null, null, null,
                    false, null, PathType.FS);

            JobInstance.enqueue(cnx, com.enioka.jqm.model.State.RUNNING, qId, jobDefdId, null, null, null, null, null, null, null, null,
                    null, false, false, null, 1, Instruction.RUN, new HashMap<>());
            JobInstance.enqueue(cnx, com.enioka.jqm.model.State.RUNNING, qId, jobDefdId, null, null, null, null, null, null, null, null,
                    null, false, false, null, 1, Instruction.RUN, new HashMap<>());
            cnx.commit();

            Properties p2 = new Properties();
            p2.put("com.enioka.jqm.jdbc.contextobject", db);
            List<com.enioka.jqm.client.api.JobInstance> res = JqmClientFactory.getClient("test", p2, false).newQuery()
                    .setQueryHistoryInstances(false).setQueryLiveInstances(true).addSortDesc(Sort.ID).setPageSize(1)
                    .setApplicationName("appName").invoke();

            Assert.assertEquals(1, res.size());
        }
    }
}
