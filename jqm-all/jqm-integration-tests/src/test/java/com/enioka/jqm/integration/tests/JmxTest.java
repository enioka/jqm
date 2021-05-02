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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Set;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Assert;
import org.junit.Test;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.engine.JqmEngineMBean;
import com.enioka.jqm.engine.QueuePollerMBean;
import com.enioka.jqm.runner.java.api.JavaJobInstanceTrackerMBean;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

public class JmxTest extends JqmBaseTest
{
    @Test
    public void jmxRemoteTest() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.KillMe", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip, 42,
                "KillApp", null, "Franquin", "ModuleMachin", "other", "other", false, cnx);
        int i = jqmClient.newJobRequest("KillApp", "TestUser").enqueue();

        // Set JMX server on free ports
        ServerSocket s1 = new ServerSocket(0);
        int port1 = s1.getLocalPort();
        ServerSocket s2 = new ServerSocket(0);
        int port2 = s2.getLocalPort();
        s1.close();
        s2.close();
        String hn = InetAddress.getLocalHost().getHostName();

        cnx.runUpdate("node_update_jmx_by_id", port1, port2, TestHelpers.node.getId());
        cnx.commit();

        // Go
        addAndStartEngine();
        TestHelpers.waitForRunning(1, 10000, cnx);
        this.sleep(1); // time to actually run, not only Loader start.

        // Connect to JMX server
        JMXServiceURL url = new JMXServiceURL("service:jmx:rmi://" + hn + ":" + port1 + "/jndi/rmi://" + hn + ":" + port2 + "/jmxrmi");
        JMXConnector cntor = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = cntor.getMBeanServerConnection();
        int count = mbsc.getMBeanCount();
        System.out.println(count);

        String[] domains = mbsc.getDomains();
        System.out.println("*** domains:");
        for (String d : domains)
        {
            System.out.println(d);
        }
        Set<ObjectInstance> mbeans = mbsc.queryMBeans(new ObjectName("com.enioka.jqm:*"), null);
        System.out.println("*** beans in com.enioka.jqm:*: ");
        for (ObjectInstance oi : mbeans)
        {
            System.out.println(oi.getObjectName());
        }
        Assert.assertTrue(mbeans.size() >= 5);
        // 1 node, 3 pollers, 1 running instance, 1 JDBC pool. The pool may not be visible due to a call to resetSingletons.

        // /////////////////
        // Loader beans
        ObjectName killBean = new ObjectName(
                "com.enioka.jqm:type=Node.Queue.JobInstance,Node=" + TestHelpers.node.getName() + ",Queue=VIPQueue,name=" + i);
        System.out.println("Name to kill: " + killBean.toString());
        mbeans = mbsc.queryMBeans(killBean, null);
        if (mbeans.isEmpty())
        {
            Assert.fail("could not find the JMX Mbean of the launched job");
        }
        JavaJobInstanceTrackerMBean proxy = JMX.newMBeanProxy(mbsc, killBean, JavaJobInstanceTrackerMBean.class);
        Assert.assertEquals("KillApp", proxy.getApplicationName());
        Assert.assertEquals((Integer) i, proxy.getId());
        Assert.assertEquals("TestUser", proxy.getUser());

        // Elements that are not set or testable reproductibly, but should not raise any exception
        proxy.getEnqueueDate();
        proxy.getKeyword1();
        proxy.getKeyword2();
        proxy.getKeyword3();
        proxy.getModule();
        proxy.getRunTimeSeconds();
        proxy.getRunTimeSeconds(); // Twice for all code paths.
        proxy.getSessionId();

        // Kill it though JMX
        proxy.kill();
        Thread.sleep(4000);

        // //////////////////
        // Engine bean
        ObjectName engine = new ObjectName("com.enioka.jqm:type=Node,name=" + TestHelpers.node.getName());
        JqmEngineMBean proxyEngine = JMX.newMBeanProxy(mbsc, engine, JqmEngineMBean.class);
        Assert.assertEquals(1, proxyEngine.getCumulativeJobInstancesCount() + proxyEngine.getCurrentlyRunningJobCount());
        Assert.assertTrue(proxyEngine.getUptime() > 0);
        proxyEngine.getVersion();
        Assert.assertTrue(proxyEngine.isAllPollersPolling());
        Assert.assertTrue(!proxyEngine.isFull());

        // //////////////////
        // Poller bean
        ObjectName poller = new ObjectName("com.enioka.jqm:type=Node.Queue,Node=" + TestHelpers.node.getName() + ",name=VIPQueue");
        QueuePollerMBean proxyPoller = JMX.newMBeanProxy(mbsc, poller, QueuePollerMBean.class);
        Assert.assertEquals(1, proxyPoller.getCumulativeJobInstancesCount() + proxyPoller.getCurrentActiveThreadCount());
        proxyPoller.getCurrentlyRunningJobCount();
        proxyPoller.getJobsFinishedPerSecondLastMinute();
        Assert.assertEquals((Integer) 40, proxyPoller.getMaxConcurrentJobInstanceCount());
        Assert.assertEquals((Integer) 1, proxyPoller.getPollingIntervalMilliseconds());

        Assert.assertTrue(proxyPoller.isActuallyPolling());
        Assert.assertTrue(!proxyPoller.isFull());

        proxyPoller.stop();

        // Done
        cntor.close();
    }
}
