package com.enioka.jqm.integration.tests;

import java.util.Calendar;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.JobDefDto;
import com.enioka.api.admin.ScheduledJob;
import com.enioka.jqm.client.api.JobDef;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.State;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

/**
 * Tests for the cron integration.
 */
public class SchedulerTest extends JqmBaseTest
{
    @Test
    public void createMeta()
    {
        int i = CreationTools.createJobDef(null, true, "doesnotexist", null, "path", TestHelpers.qVip, -1, "TestJqmApplication",
                "appFreeName", "TestModule", "kw1", "kw2", "kw3", false, cnx);

        JobDefDto dto = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(0, dto.getParameters().size());

        // Add a schedule.
        dto.addSchedule(ScheduledJob.create("5 * * * *"));
        MetaService.upsertJobDef(cnx, dto);
        cnx.commit();

        JobDefDto dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(1, dto2.getSchedules().size());
        Assert.assertEquals("5 * * * *", dto2.getSchedules().get(0).getCronExpression());
        Assert.assertEquals(0, dto2.getSchedules().get(0).getParameters().size());
        Calendar update = dto2.getSchedules().get(0).getLastUpdated();
        sleepms(1);

        // Update the dto without modifying anything - update date should stay the same.
        MetaService.upsertJobDef(cnx, dto);
        cnx.commit();
        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(update.getTimeInMillis(), dto2.getSchedules().get(0).getLastUpdated().getTimeInMillis());

        // Add a parameter to the schedule. Update date should change.
        dto2.getSchedules().get(0).addParameter("houba", "hop");
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();
        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(1, dto2.getSchedules().get(0).getParameters().size());
        Assert.assertNotEquals(update.getTimeInMillis(), dto2.getSchedules().get(0).getLastUpdated().getTimeInMillis());

        // Remove the parameter
        dto2.getSchedules().get(0).removeParameter("houba");
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();
        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(0, dto2.getSchedules().get(0).getParameters().size());

        // Remove the schedule
        dto2.getSchedules().clear();
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();
        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(0, dto2.getSchedules().size());

        // Now add two schedules and remove one (it's another code path than simply clearing the schedules).
        dto2.addSchedule(ScheduledJob.create("5 * * * *"));
        dto2.addSchedule(ScheduledJob.create("7 * * * *"));
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();

        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(2, dto2.getSchedules().size());

        ScheduledJob toDelete = null;
        for (ScheduledJob sj : dto2.getSchedules())
        {
            if (sj.getCronExpression().equals("5 * * * *"))
            {
                toDelete = sj;
                break;
            }
        }
        dto2.getSchedules().remove(toDelete);
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();

        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(1, dto2.getSchedules().size());
        Assert.assertEquals("7 * * * *", dto2.getSchedules().get(0).getCronExpression());

        // Now play with parameter overloads. Update date should change.
        update = dto2.getSchedules().get(0).getLastUpdated();
        dto2.getSchedules().get(0).addParameter("test1", "value1");
        dto2.getSchedules().get(0).addParameter("test2", "value2");
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();

        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertNotEquals(update.getTimeInMillis(), dto2.getSchedules().get(0).getLastUpdated().getTimeInMillis());
        Assert.assertEquals(2, dto2.getSchedules().get(0).getParameters().size());

        dto2.getSchedules().get(0).removeParameter("test1");
        MetaService.upsertJobDef(cnx, dto2);
        cnx.commit();

        dto2 = MetaService.getJobDef(cnx, i);
        Assert.assertEquals(1, dto2.getSchedules().get(0).getParameters().size());
        Assert.assertEquals("value2", dto2.getSchedules().get(0).getParameters().get("test2"));
    }

    @Test // Commented - waiting for one minute is long.
    public void testSimpleSchedule()
    {
        int id = CreationTools.createJobDef(null, true, "pyl.EngineApiSendMsg", null, "jqm-tests/jqm-test-pyl/target/test.jar",
                TestHelpers.qVip, 42, "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        int scheduleId = jqmClient.newJobRequest("MarsuApplication", "test user").setRecurrence("* * * * *").addParameter("key1", "value1")
                .enqueue();

        JobDef jd_client = jqmClient.getJobDefinition("MarsuApplication");
        Assert.assertEquals(id, (int) jd_client.getId());
        Assert.assertEquals(1, jd_client.getSchedules().size());
        Assert.assertEquals(scheduleId, jd_client.getSchedules().get(0).getId());
        Assert.assertEquals("* * * * *", jd_client.getSchedules().get(0).getCronExpression());

        addAndStartEngine();

        TestHelpers.waitFor(1, 150000, cnx);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));

        JobDefDto jd = MetaService.getJobDef(cnx, id);
        Assert.assertEquals(1, jd.getSchedules().size());

        jqmClient.removeRecurrence(scheduleId);

        jd = MetaService.getJobDef(cnx, id);
        Assert.assertEquals(0, jd.getSchedules().size());

        Assert.assertTrue(jqmClient.newQuery().invoke().get(0).isFromSchedule());
    }

    @Test // Commented - waiting for one minute is long.
    public void testDelayedJob()
    {
        CreationTools.createJobDef(null, true, "pyl.EngineApiSendMsg", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        Calendar runAt = Calendar.getInstance();
        runAt.set(Calendar.MILLISECOND, 0); // Not needed in normal operations, but we will compare at the end.
        runAt.set(Calendar.SECOND, 0);
        runAt.add(Calendar.MINUTE, 1);

        jqmClient.newJobRequest("MarsuApplication", "testuser").setRunAfter(runAt).enqueue();
        Assert.assertEquals(1, TestHelpers.getQueueAllCount(cnx));
        Assert.assertEquals(State.SCHEDULED, jqmClient.newQuery().setQueryLiveInstances(true).invoke().get(0).getState());
        Assert.assertTrue(jqmClient.newQuery().setQueryLiveInstances(true).invoke().get(0).isFromSchedule());
        Assert.assertEquals(runAt, jqmClient.newQuery().setQueryLiveInstances(true).invoke().get(0).getRunAfter());

        addAndStartEngine();

        TestHelpers.waitFor(1, 150000, cnx);
        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));

        Assert.assertTrue(jqmClient.newQuery().invoke().get(0).isFromSchedule());
        Assert.assertTrue(jqmClient.newQuery().invoke().get(0).getBeganRunningDate().after(runAt));
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
