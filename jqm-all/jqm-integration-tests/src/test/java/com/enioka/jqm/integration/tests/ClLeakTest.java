package com.enioka.jqm.integration.tests;

import java.lang.management.ManagementFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.test.helpers.TestHelpers;

public class ClLeakTest extends JqmBaseTest
{
    @Test
    public void testJmxLeak() throws Exception
    {
        int i = JqmSimpleTest.create(cnx, "pyl.EngineJmxLeak").addWaitTime(10000).expectOk(0).run(this);

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("com.test:type=Node,name=test");
        mbs.getAttribute(name, "One");

        // Stop the job. Its MBean(s) should be cleaned up by the engine.
        jqmClient.killJob(i);
        TestHelpers.waitFor(1, 3000, cnx);

        // Check the bean is really dead
        try
        {
            mbs.getAttribute(name, "One");
            Assert.fail();
        }
        catch (InstanceNotFoundException e)
        {
            // It's OK!
        }
    }
}
