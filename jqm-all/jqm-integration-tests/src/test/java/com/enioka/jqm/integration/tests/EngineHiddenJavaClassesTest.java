package com.enioka.jqm.integration.tests;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class EngineHiddenJavaClassesTest extends JqmBaseTest
{

    /**
     * Hide java.math.BigInteger from parent class loader causing job to fail
     */
    @Test
    public void testHiddenBigInteger() throws Exception
    {
        addAndStartEngine();

        CreationTools.createJobDef(null, true, "pyl.EngineHiddenJavaClasses", null, "jqm-tests/jqm-test-pyl/target/test.jar",
                TestHelpers.qVip, -1, "EngineHiddenJavaClasses", null, null, null, null, null, false, cnx, null, false,
                "java.lang.Marsu,java.math.*");
        jqmClient.newJobRequest("EngineHiddenJavaClasses", null).enqueue();

        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    /**
     * Hide java.net.* but not java.math.BigInteger, job exits successfully
     */
    @Test
    public void testNoMatches() throws Exception
    {
        addAndStartEngine();

        CreationTools.createJobDef(null, true, "pyl.EngineHiddenJavaClasses", null, "jqm-tests/jqm-test-pyl/target/test.jar",
                TestHelpers.qVip, -1, "EngineHiddenJavaClasses", null, null, null, null, null, false, cnx, null, false, "java.net.*");
        jqmClient.newJobRequest("EngineHiddenJavaClasses", null).enqueue();

        TestHelpers.waitFor(1, 10000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

}
