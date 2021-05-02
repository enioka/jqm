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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.JobDefDto;
import com.enioka.jqm.client.api.JobDef;
import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.Queue;
import com.enioka.jqm.client.api.QueueStatus;
import com.enioka.jqm.client.api.State;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

@Ignore // TODO
public class ClientApiTest extends JqmBaseTest
{
    @Test
    public void testRestartJob() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();

        TestHelpers.waitFor(1, 10000, cnx);
        jqmClient.enqueueFromHistory(i);
        TestHelpers.waitFor(2, 10000, cnx);

        Assert.assertEquals(2, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));

        List<JobInstance> res = jqmClient.newQuery().addSortAsc(Sort.DATEENQUEUE).invoke();

        Assert.assertEquals(2, res.size());
        Assert.assertEquals("MarsuApplication", res.get(0).getApplicationName());
        Assert.assertEquals("MarsuApplication", res.get(1).getApplicationName());
    }

    @Test
    public void testHistoryFields() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other2", true, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").setSessionID("session42").setKeyword1("k1").setKeyword2("k2")
                .enqueue();

        addAndStartEngine();

        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        JobInstance h = jqmClient.getJob(i);

        SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        jqmlogger.debug("enqueueDate: " + df.format(h.getEnqueueDate().getTime()));
        jqmlogger.debug("ExecutionDate: " + df.format(h.getBeganRunningDate().getTime()));
        jqmlogger.debug("EndDate: " + df.format(h.getEndDate().getTime()));

        Assert.assertTrue(h.getEnqueueDate() != null);
        Assert.assertTrue(h.getUser() != null);
        Assert.assertTrue(h.getEndDate() != null);
        Assert.assertTrue(h.getBeganRunningDate() != null);
        Assert.assertTrue(h.getSessionID() != null);
        Assert.assertEquals("session42", h.getSessionID());

        JobInstance ji = jqmClient.newQuery().setApplicationName("MarsuApplication").invoke().get(0);
        Assert.assertEquals("ModuleMachin", ji.getDefinitionKeyword1());
        Assert.assertEquals("other", ji.getDefinitionKeyword2());
        Assert.assertEquals("other2", ji.getDefinitionKeyword3());
        Assert.assertEquals("k1", ji.getKeyword1());
        Assert.assertEquals("k2", ji.getKeyword2());
        Assert.assertEquals(null, ji.getKeyword3());
    }

    @Test
    public void testKillJob() throws Exception
    {
        int i = JqmSimpleTest.create(cnx, "pyl.KillMe").expectOk(0).addWaitTime(3000).run(this);

        jqmClient.killJob(i);
        TestHelpers.waitFor(1, 3000, cnx);

        List<JobInstance> res = jqmClient.newQuery().invoke();
        Assert.assertEquals(1, res.size());
        Assert.assertEquals(State.CRASHED, res.get(0).getState());
    }

    @Test
    public void testGetMsg() throws Exception
    {
        boolean success = false;
        boolean success2 = false;
        boolean success3 = false;

        int i = JqmSimpleTest.create(cnx, "pyl.EngineApiSend3Msg").run(this);

        List<String> ress = jqmClient.getJobMessages(i);

        Assert.assertEquals(3, ress.size());

        for (int k = 0; k < ress.size(); k++)
        {
            if (ress.get(k).equals("Les marsus sont nos amis, il faut les aimer aussi!"))
            {
                success = true;
            }
            if (ress.get(k).equals("Les marsus sont nos amis, il faut les aimer aussi!2"))
            {
                success2 = true;
            }
            if (ress.get(k).equals("Les marsus sont nos amis, il faut les aimer aussi!3"))
            {
                success3 = true;
            }
        }

        Assert.assertEquals(true, success);
        Assert.assertEquals(true, success2);
        Assert.assertEquals(true, success3);
    }

    @Test
    public void testGetProgress() throws Exception
    {
        int i = JqmSimpleTest.create(cnx, "pyl.EngineApiProgress").addWaitMargin(10000).run(this);
        Integer k = jqmClient.getJobProgress(i);
        Assert.assertEquals((Integer) 50, k);
    }

    @Test
    public void testPauseInQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.pauseQueuedJob(i);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 5000, cnx);

        List<JobInstance> res1 = jqmClient.newQuery().addSortAsc(Sort.DATEENQUEUE).invoke();
        List<JobInstance> res2 = jqmClient.newQuery().addSortAsc(Sort.DATEENQUEUE).setQueryHistoryInstances(false)
                .setQueryLiveInstances(true).invoke();

        Assert.assertEquals(1, res1.size());
        Assert.assertEquals(1, res2.size());
        Assert.assertEquals(State.HOLDED, res2.get(0).getState());
        Assert.assertEquals(State.ENDED, res1.get(0).getState());
    }

    @Test
    public void testCancelJob() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.cancelJob(i);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(2, 5000, cnx);

        List<JobInstance> res = jqmClient.newQuery().addSortAsc(Sort.DATEENQUEUE).invoke();
        Assert.assertEquals(2, res.size());
        Assert.assertEquals(State.CANCELLED, res.get(0).getState());
        Assert.assertEquals(State.ENDED, res.get(1).getState());
    }

    @Test
    public void testChangeQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.setJobQueue(i, TestHelpers.qSlow);

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        List<JobInstance> res = jqmClient.newQuery().addSortAsc(Sort.DATEENQUEUE).invoke();
        Assert.assertEquals(1, res.size());
        Assert.assertEquals(State.ENDED, res.get(0).getState());
        Assert.assertEquals("SlowQueue", res.get(0).getQueue().getName());
    }

    @Test
    public void testDelJobInQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.deleteJob(i);

        addAndStartEngine();
        Thread.sleep(1000);

        Assert.assertEquals(0, TestHelpers.getHistoryAllCount(cnx));
        Assert.assertEquals(0, TestHelpers.getQueueAllCount(cnx));
    }

    @Test
    public void testResumeInQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        jqmClient.pauseQueuedJob(i);
        addAndStartEngine();
        Thread.sleep(3000);
        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        jqmClient.resumeQueuedJob(i);

        TestHelpers.waitFor(1, 5000, cnx);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
    }

    @Test
    public void testEnqueueWithQueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").setQueueName("NormalQueue").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        JobInstance ji = jqmClient.getJob(i);
        Assert.assertEquals("NormalQueue", ji.getQueue().getName());
    }

    /**
     * Temp dir should be removed after run
     */
    @Test
    public void testTempDir() throws Exception
    {
        int i = JqmSimpleTest.create(cnx, "pyl.EngineApiTmpDir").run(this);

        File tmpDir = new File(FilenameUtils.concat(TestHelpers.node.getTmpDirectory(), "" + i));
        Assert.assertFalse(tmpDir.isDirectory());
    }

    @Test
    public void testTags() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.EngineApiTags", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "keyword1", null, "keyword3", false, cnx);
        jqmClient.newJobRequest("MarsuApplication", "TestUser").setKeyword1("Houba").setKeyword3("Meuh").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));

        List<JobInstance> res = jqmClient.newQuery().invoke();

        Assert.assertEquals(1, res.size());
        JobInstance h = res.get(0);

        Assert.assertEquals("Houba", h.getKeyword1());
        Assert.assertEquals(null, h.getKeyword2());
        Assert.assertEquals("Meuh", h.getKeyword3());

        Assert.assertEquals("keyword1", h.getDefinitionKeyword1());
        Assert.assertEquals(null, h.getDefinitionKeyword2());
        Assert.assertEquals("keyword3", h.getDefinitionKeyword3());
    }

    @Test
    public void testPauseResumeRunning() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.CallYieldAtOnce", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        // Pause the JI in advance, so that it will receive the instruction on startup.
        jqmClient.pauseRunningJob(i);
        addAndStartEngine();
        TestHelpers.waitForRunning(1, 10000, cnx);
        Thread.sleep(2000);
        Assert.assertEquals(0, TestHelpers.getHistoryAllCount(cnx)); // Still running.

        // Pause should leave a message.
        List<String> msgs = jqmClient.getJobMessages(i);
        Assert.assertEquals(1, msgs.size());
        Assert.assertTrue(msgs.get(0).toLowerCase().contains("pause"));

        // Now resume.
        jqmClient.resumeRunningJob(i);
        TestHelpers.waitFor(1, 10000, cnx);

        msgs = jqmClient.getJobMessages(i);
        Assert.assertEquals(2, msgs.size());

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
    }

    @Test
    public void testPauseResumeQueue()
    {
        CreationTools.createJobDef(null, true, "pyl.CallYieldAtOnce", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        Queue qV = null;
        for (Queue q : jqmClient.getQueues())
        {
            if (q.getId() == TestHelpers.qVip)
            {
                qV = q;
                break;
            }
        }

        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        addAndStartEngine();
        TestHelpers.waitFor(1, 10000, cnx);

        // 46: 40 for dpVip + 3 for dpVipMix + dpVipMix2, see TestHelpers
        Assert.assertEquals(46, jqmClient.getQueueEnabledCapacity(qV));

        jqmClient.pauseQueue(qV);

        Assert.assertEquals(0, jqmClient.getQueueEnabledCapacity(qV));

        this.sleep(1); // This sleep is because: parameters are refreshed on poller loop start, so let
                       // the loop end.
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        this.sleep(1);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getQueueAllCount(cnx));
        Assert.assertEquals(0, TestHelpers.getQueueRunningCount(cnx));
        Assert.assertEquals(QueueStatus.PAUSED, jqmClient.getQueueStatus(qV));

        jqmClient.resumeQueue(qV);

        Assert.assertEquals(QueueStatus.RUNNING, jqmClient.getQueueStatus(qV));
        TestHelpers.waitFor(2, 10000, cnx);
        Assert.assertEquals(2, TestHelpers.getOkCount(cnx));
    }

    @Test
    public void testClearQueue()
    {
        CreationTools.createJobDef(null, true, "pyl.CallYieldAtOnce", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        Queue qV = null;
        for (Queue q : jqmClient.getQueues())
        {
            if (q.getId() == TestHelpers.qVip)
            {
                qV = q;
                break;
            }
        }

        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        Assert.assertEquals(3, TestHelpers.getQueueAllCount(cnx));

        jqmClient.clearQueue(qV);

        Assert.assertEquals(0, TestHelpers.getQueueAllCount(cnx));
    }

    @Test
    public void testChangeXVerbs()
    {
        int idJobDef = CreationTools.createJobDef(null, true, "pyl.CallYieldAtOnce", null, "jqm-tests/jqm-test-pyl/target/test.jar",
                TestHelpers.qVip, 42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();

        //////////////////////////////////
        // JI priority

        // No priority by default
        Assert.assertEquals(null, jqmClient.getJob(i).getPriority());

        // Change it.
        jqmClient.setJobPriority(i, 5);
        Assert.assertEquals(5, (int) jqmClient.getJob(i).getPriority());

        //////////////////////////////////
        // JI run after

        Calendar after = Calendar.getInstance();
        after.add(Calendar.YEAR, 1);
        int i2 = jqmClient.newJobRequest("MarsuApplication", "TestUser").setRunAfter(after).enqueue();

        after = Calendar.getInstance();
        after.add(Calendar.YEAR, -1);
        jqmClient.setJobRunAfter(i2, after);
        Assert.assertTrue(com.enioka.jqm.model.JobInstance.select_id(cnx, i2).getNotBefore().before(Calendar.getInstance()));

        int i4 = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        Assert.assertNull(com.enioka.jqm.model.JobInstance.select_id(cnx, i4).getNotBefore());
        jqmClient.setJobRunAfter(i4, after);
        Assert.assertTrue(com.enioka.jqm.model.JobInstance.select_id(cnx, i4).getNotBefore().before(Calendar.getInstance()));

        //////////////////////////////////
        // Schedule

        int i3 = jqmClient.newJobRequest("MarsuApplication", "TestUser").setPriority(3).setRecurrence("* * * * *").enqueue();

        JobDefDto jd = MetaService.getJobDef(cnx, idJobDef);
        Assert.assertEquals(3, (int) jd.getSchedules().get(0).getPriority());
        Assert.assertEquals("* * * * *", jd.getSchedules().get(0).getCronExpression());
        Assert.assertEquals(null, jd.getSchedules().get(0).getQueue());

        JobDef jd_client = jqmClient.getJobDefinition("MarsuApplication");
        Assert.assertEquals(idJobDef, (int) jd_client.getId());
        Assert.assertEquals(1, jd_client.getSchedules().size());
        Assert.assertEquals(i3, jd_client.getSchedules().get(0).getId());
        Assert.assertEquals("* * * * *", jd_client.getSchedules().get(0).getCronExpression());

        jqmClient.setScheduleRecurrence(i3, "1 * * * *");
        jd = MetaService.getJobDef(cnx, idJobDef);
        Assert.assertEquals("1 * * * *", jd.getSchedules().get(0).getCronExpression());

        jqmClient.setScheduleQueue(i3, TestHelpers.qSlow);
        jd = MetaService.getJobDef(cnx, idJobDef);
        Assert.assertEquals(TestHelpers.qSlow, jd.getSchedules().get(0).getQueue());

        jqmClient.setSchedulePriority(i3, 4);
        jd = MetaService.getJobDef(cnx, idJobDef);
        Assert.assertEquals(4, (int) jd.getSchedules().get(0).getPriority());
    }

    @Test
    public void testMiscVerbs()
    {
        int idJobDef1 = CreationTools.createJobDef(null, true, "pyl.CallYieldAtOnce", null, "jqm-tests/jqm-test-pyl/target/test.jar",
                TestHelpers.qVip, 42, "MarsuApplication", "AppTag", "Franquin", "ModuleMachin", "other", "other", false, cnx);
        CreationTools.createJobDef(null, true, "pyl.SecThrow", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42, "Dies",
                null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        CreationTools.createJobDef(null, true, "pyl.EngineApiSendMsg", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "Message", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);

        List<JobDef> jds = jqmClient.getJobDefinitions();
        Assert.assertEquals(3, jds.size());

        jds = jqmClient.getJobDefinitions("AppTag");
        Assert.assertEquals(1, jds.size());

        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        addAndStartEngine();
        TestHelpers.waitFor(1, 5000, cnx);

        jqmClient.enqueueFromHistory(i);
        TestHelpers.waitFor(2, 5000, cnx);

        int idRec = jqmClient.newJobRequest("MarsuApplication", "TestUser").setRecurrence("* * * * *").enqueue();
        Assert.assertEquals(1, MetaService.getJobDef(cnx, idJobDef1).getSchedules().size());
        jqmClient.removeRecurrence(idRec);
        Assert.assertEquals(0, MetaService.getJobDef(cnx, idJobDef1).getSchedules().size());

        i = jqmClient.newJobRequest("Dies", "TestUser").enqueue();
        TestHelpers.waitFor(3, 5000, cnx);
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
        jqmClient.restartCrashedJob(i);
        TestHelpers.waitFor(3, 5000, cnx);
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));

        Assert.assertEquals(3, jqmClient.getJobs().size());
        Assert.assertEquals(0, jqmClient.getActiveJobs().size());
        Assert.assertEquals(0, jqmClient.getUserActiveJobs("TestUser").size());

        i = jqmClient.newJobRequest("Message", "TestUser").enqueue();
        TestHelpers.waitFor(4, 5000, cnx);
        Assert.assertEquals(1, jqmClient.getJobMessages(i).size());

        jqmClient.dispose();
    }

    @Test
    public void testStartHeld()
    {
        CreationTools.createJobDef(null, true, "pyl.EngineApiSendMsg", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        int i = jqmClient.newJobRequest("MarsuApplication", "testuser").startHeld().enqueue();
        addAndStartEngine();

        // Should not run.
        sleepms(1000);
        Assert.assertEquals(1, TestHelpers.getQueueAllCount(cnx));
        Assert.assertEquals(State.HOLDED, jqmClient.newQuery().setQueryLiveInstances(true).invoke().get(0).getState());

        // Resume at will.
        jqmClient.resumeQueuedJob(i);
        TestHelpers.waitFor(1, 10000, cnx);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
    }
}
