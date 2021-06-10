package com.enioka.jqm.tools;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.enioka.jqm.api.JobRequest;
import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class DbFailTest extends JqmBaseTest
{
    @Before
    public void before()
    {
        assumeNotDb2();
        assumeNotOracle();
    }

    @Test
    public void testDbFailure() throws Exception
    {
        this.addAndStartEngine();
        this.simulateDbFailure(2);

        // DB connection lost will stop pollers

        this.sleep(5);

        // Once DB connection is restored all pollers restarted
        Assert.assertTrue(this.waitForPollersArePolling());
    }

    @Test
    public void testDbDoubleFailure() throws Exception
    {
        this.addAndStartEngine();
        this.simulateDbFailure(2);

        this.sleep(2);
        Assert.assertTrue(this.waitForPollersArePolling());

        // cnx was closed in previous simulateDbFailure()
        cnx = getNewDbSession();

        this.simulateDbFailure(2);

        this.sleep(2);

        Assert.assertTrue(this.waitForPollersArePolling());
    }

    @Test
    public void testDbTranscientFailure() throws Exception
    {
        // Set a real polling interval to allow the failure to be unseen by the poller
        cnx.runUpdate("dp_update_interval_by_id", 3000, TestHelpers.dpVip.getId());
        cnx.commit();

        this.addAndStartEngine();
        this.sleep(2); // first poller loop

        this.simulateDbFailure(2);

        this.sleep(5);

        Assert.assertTrue(this.waitForPollersArePolling());
    }

    // Job ends OK during db failure.
    @Test
    public void testDbFailureWithRunningJob() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.Wait", "jqm-test-pyl-nodep").addRuntimeParameter("p1", "4000").expectOk(0).run(this);
        this.sleep(2);

        this.simulateDbFailure(5);
        TestHelpers.waitFor(1, 10000, this.getNewDbSession());

        Assert.assertEquals(1, TestHelpers.getOkCount(this.getNewDbSession()));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(this.getNewDbSession()));
    }

    // Job ends KO during db failure.
    @Test
    public void testDbFailureWithRunningJobKo() throws Exception
    {
        JqmSimpleTest.create(cnx, "pyl.KillMe").expectOk(0).run(this);
        this.sleep(2);

        this.simulateDbFailure(5);
        TestHelpers.waitFor(1, 10000, this.getNewDbSession());

        Assert.assertEquals(0, TestHelpers.getOkCount(this.getNewDbSession()));
        // TODO
        //Assert.assertEquals(1, TestHelpers.getNonOkCount(this.getNewDbSession()));
    }

    // Many jobs starting & running during failure
    @Test
    public void testDbFailureUnderLoad() throws Exception
    {
        // Many starting jobs simultaneously
        cnx.runUpdate("dp_update_threads_by_id", 50, TestHelpers.dpVip.getId());
        cnx.commit();
        TestHelpers.setNodesLogLevel("INFO", cnx);

        CreationTools.createJobDef(null, true, "pyl.Nothing", null, "jqm-tests/jqm-test-pyl-nodep/target/test.jar", TestHelpers.qVip, -1,
                "TestJqmApplication", "appFreeName", "TestModule", "kw1", "kw2", "kw3", false, cnx);

        JobRequest j = new JobRequest("TestJqmApplication", "TestUser");
        for (int i = 0; i < 1000; i++)
        {
            JqmClientFactory.getClient().enqueue(j);
        }

        addAndStartEngine();

        this.sleep(1);
        jqmlogger.info("Stopping db");
        this.simulateDbFailure(2);

        Assert.assertTrue(this.waitForPollersArePolling());

        TestHelpers.waitFor(1000, 120000, this.getNewDbSession());
        this.sleep(5);

        Assert.assertEquals(1000, TestHelpers.getOkCount(this.getNewDbSession()));

        Assert.assertTrue(this.engines.get("localhost").areAllPollersPolling());
    }
}
