package com.enioka.jqm.integration.tests;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.JobDefParameter;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class JqmSimpleTest
{
    private DbConn cnx;
    private Integer jd = null;
    private Map<String, String> runtimePrms = new HashMap<>();
    private List<String> nodeNames = new ArrayList<>();
    private String sessionId = null;

    private int expectedOk = 1, expectedNonOk = 0;
    private int waitMsMin = 0, waitMarginMs = 0;

    private JqmSimpleTest(DbConn cnx, String className, String artifactName)
    {
        this.cnx = cnx;
        this.jd = CreationTools.createJobDef(null, true, className, null, "jqm-tests/" + artifactName + "/target/test.jar",
                TestHelpers.qVip, -1, "TestJqmApplication", "appFreeName", "TestModule", "kw1", "kw2", "kw3", false, cnx);
        nodeNames.add("localhost");
    }

    public static JqmSimpleTest create(DbConn cnx, String className)
    {
        return new JqmSimpleTest(cnx, className, "jqm-test-pyl");
    }

    public static JqmSimpleTest create(DbConn cnx, String className, String artifact)
    {
        return new JqmSimpleTest(cnx, className, artifact);
    }

    public JqmSimpleTest addDefParameter(String key, String value)
    {
        JobDefParameter.create(cnx, key, value, jd);
        cnx.commit();
        return this;
    }

    public JqmSimpleTest addRuntimeParameter(String key, String value)
    {
        this.runtimePrms.put(key, value);
        return this;
    }

    public JqmSimpleTest addEngine(String nodeName)
    {
        nodeNames.add(nodeName);
        return this;
    }

    public JqmSimpleTest expectOk(int expected)
    {
        this.expectedOk = expected;
        return this;
    }

    public JqmSimpleTest expectNonOk(int expected)
    {
        this.expectedNonOk = expected;
        return this;
    }

    public JqmSimpleTest setSessionId(String id)
    {
        this.sessionId = id;
        return this;
    }

    public JqmSimpleTest setExternal()
    {
        cnx.runUpdate("jd_update_set_external_by_id", jd);
        cnx.commit();
        return this;
    }

    /**
     * Time always waited (even if jobs have ended)
     */
    public JqmSimpleTest addWaitTime(int ms)
    {
        this.waitMsMin = ms;
        return this;
    }

    /**
     * Time added to the 9000ms time "waiting for job end".
     */
    public JqmSimpleTest addWaitMargin(int ms)
    {
        this.waitMarginMs = ms;
        return this;
    }

    public Integer run(JqmBaseTest test)
    {
        int nbExpected = expectedNonOk + expectedOk;

        for (String nodeName : nodeNames)
        {
            test.addAndStartEngine(nodeName);
        }
        Integer i = JqmClientFactory.getClient().newJobRequest("TestJqmApplication", "TestUser").setSessionID(sessionId)
                .setParameters(runtimePrms).enqueue();
        TestHelpers.waitFor(nbExpected, 9000 + waitMarginMs + nbExpected * 2000, cnx);
        if (waitMsMin > 0)
        {
            try
            {
                Thread.sleep(waitMsMin);
            }
            catch (InterruptedException e)
            {
                // not an issue during tests.
            }
        }

        Assert.assertEquals(expectedOk, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(expectedNonOk, TestHelpers.getNonOkCount(cnx));

        return i;
    }
}
