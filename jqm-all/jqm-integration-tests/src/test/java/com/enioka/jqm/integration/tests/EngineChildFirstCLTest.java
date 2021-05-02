package com.enioka.jqm.integration.tests;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class EngineChildFirstCLTest extends JqmBaseTest
{

    /**
     * Test with parent first method
     */
    @Test
    public void testParentFirst() throws Exception
    {
        addAndStartEngine();

        CreationTools.createJobDef(null, true, "pyl.EngineChildFirstCL", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip,
                -1, "EngineChildFirstCL", null, null, null, null, null, false, cnx, null, false);
        jqmClient.newJobRequest("EngineChildFirstCL", null).enqueue();

        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    /**
     * Test with child first method
     */
    @Test
    public void testChildFirst() throws Exception
    {
        addAndStartEngine();

        CreationTools.createJobDef(null, true, "pyl.EngineChildFirstCL", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip,
                -1, "EngineChildFirstCL", null, null, null, null, null, false, cnx, null, true);
        jqmClient.newJobRequest("EngineChildFirstCL", null).enqueue();

        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }
}
