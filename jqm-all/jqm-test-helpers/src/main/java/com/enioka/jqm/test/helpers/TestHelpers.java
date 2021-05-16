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

package com.enioka.jqm.test.helpers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.RRole;

public class TestHelpers
{
    public static Logger jqmlogger = LoggerFactory.getLogger(TestHelpers.class);

    public static Integer qVip, qNormal, qSlow, qVip2, qNormal2, qSlow2, qVip3, qNormal3, qSlow3;
    public static Node node, node2, node3, nodeMix, nodeMix2;

    public static DeploymentParameter dpVip, dpNormal, dpSlow, dpVip2, dpNormal2, dpSlow2, dpVip3, dpNormal3, dpSlow3, dpVipMix, dpVipMix2;

    public static void createTestData(DbConn cnx)
    {
        CreationTools.createDatabaseProp("jdbc/marsu", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:testdb", "SA", "", cnx,
                "SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS", null);

        GlobalParameter.create(cnx, "mavenRepo", "http://repo1.maven.org/maven2/");
        GlobalParameter.create(cnx, "defaultConnection", "jdbc/marsu");
        GlobalParameter.create(cnx, "logFilePerLaunch", "false");
        GlobalParameter.create(cnx, "internalPollingPeriodMs", "60000");
        GlobalParameter.create(cnx, "mavenSettingsCL", "META-INF/settings.xml");
        GlobalParameter.create(cnx, "disableWsApi", "true");
        GlobalParameter.create(cnx, "enableWsApiSsl", "false");
        GlobalParameter.create(cnx, "enableWsApiAuth", "true");
        GlobalParameter.create(cnx, "disableVerboseStartup", "true");

        TestHelpers.qVip = Queue.create(cnx, "VIPQueue", "Queue for the winners", true);
        TestHelpers.qNormal = Queue.create(cnx, "NormalQueue", "Queue for the ordinary job", false);
        TestHelpers.qSlow = Queue.create(cnx, "SlowQueue", "Queue for the bad guys", false);

        TestHelpers.qVip2 = Queue.create(cnx, "VIPQueue2", "Queue for the winners2", false);
        TestHelpers.qNormal2 = Queue.create(cnx, "NormalQueue2", "Queue for the ordinary job2", false);
        TestHelpers.qSlow2 = Queue.create(cnx, "SlowQueue2", "Queue for the bad guys2", false);

        TestHelpers.qVip3 = Queue.create(cnx, "VIPQueue3", "Queue for the winners3", false);
        TestHelpers.qNormal3 = Queue.create(cnx, "NormalQueue3", "Queue for the ordinary job3", false);
        TestHelpers.qSlow3 = Queue.create(cnx, "SlowQueue3", "Queue for the bad guys3", false);

        String dns = getLocalHostName();
        TestHelpers.node = Node.create(cnx, "localhost", 0, "./target/outputfiles/", "./../", "./target/tmp", dns, "DEBUG");
        TestHelpers.node2 = Node.create(cnx, "localhost2", 0, "./target/outputfiles/", "./../", "./target/tmp", dns, "DEBUG");
        TestHelpers.node3 = Node.create(cnx, "localhost3", 0, "./target/outputfiles/", "./../", "./target/tmp", dns, "DEBUG");
        TestHelpers.nodeMix = Node.create(cnx, "localhost4", 0, "./target/outputfiles/", "./../", "./target/tmp", dns, "DEBUG");
        TestHelpers.nodeMix2 = Node.create(cnx, "localhost5", 0, "./target/outputfiles/", "./../", "./target/tmp", dns, "DEBUG");

        TestHelpers.dpVip = DeploymentParameter.create(cnx, node, 40, 1, qVip);
        TestHelpers.dpVipMix = DeploymentParameter.create(cnx, nodeMix, 3, 1, qVip);
        TestHelpers.dpVipMix2 = DeploymentParameter.create(cnx, nodeMix2, 3, 1, qVip);
        TestHelpers.dpNormal = DeploymentParameter.create(cnx, node, 2, 300, qNormal);
        TestHelpers.dpSlow = DeploymentParameter.create(cnx, node, 1, 1000, qSlow);

        TestHelpers.dpVip2 = DeploymentParameter.create(cnx, node2, 3, 100, qVip2);
        TestHelpers.dpNormal2 = DeploymentParameter.create(cnx, node2, 2, 300, qNormal2);
        TestHelpers.dpSlow2 = DeploymentParameter.create(cnx, node2, 1, 1000, qSlow2);

        TestHelpers.dpVip3 = DeploymentParameter.create(cnx, node3, 3, 100, qVip3);
        TestHelpers.dpNormal3 = DeploymentParameter.create(cnx, node3, 2, 300, qNormal3);
        TestHelpers.dpSlow3 = DeploymentParameter.create(cnx, node3, 1, 1000, qSlow3);

        if (!(new File(TestHelpers.node.getDlRepo())).isDirectory() && !(new File(TestHelpers.node.getDlRepo())).mkdir())
        {
            throw new RuntimeException("could not create output directory");
        }

        CreationTools.createMailSession(cnx, "mail/default", "localhost", 10025, false, "testlogin", "testpassword");

        RRole.create(cnx, "administrator", "super admin", "*:*");
        RRole.create(cnx, "client power user", "can use the full client API", "node:read", "queue:read", "job_instance:*", "jd:read",
                "logs:read", "queue_position:create", "files:read");
        RRole.create(cnx, "client read only", "can query job instances and get their files", "queue:read", "job_instance:read", "logs:read",
                "files:read");

        cnx.commit();
    }

    public static void cleanup(DbConn cnx)
    {
        cleanup(cnx, false);
    }

    public static void cleanup(DbConn cnx, boolean onlyDb)
    {
        cnx.runUpdate("globalprm_delete_all");
        cnx.runUpdate("deliverable_delete_all");
        cnx.runUpdate("dp_delete_all");
        cnx.runUpdate("message_delete_all");
        cnx.runUpdate("history_delete_all");
        cnx.runUpdate("sjprm_delete_all");
        cnx.runUpdate("sj_delete_all");
        cnx.runUpdate("jdprm_delete_all");
        cnx.runUpdate("jiprm_delete_all");
        cnx.runUpdate("ji_delete_all");
        cnx.runUpdate("node_delete_all");
        cnx.runUpdate("jd_delete_all");
        cnx.runUpdate("q_delete_all");
        cnx.runUpdate("jndiprm_delete_all");
        cnx.runUpdate("jndi_delete_all");
        cnx.runUpdate("pki_delete_all");
        cnx.runUpdate("perm_delete_all");
        cnx.runUpdate("role_delete_all");
        cnx.runUpdate("user_delete_all");
        cnx.runUpdate("clehprm_delete_all");
        cnx.runUpdate("cleh_delete_all");
        cnx.runUpdate("cl_delete_all");

        cnx.commit();

        if (!onlyDb)
        {
            try
            {
                // Conf dir may contain certificates and certificate stores
                if ((new File("./conf")).isDirectory())
                {
                    FileUtils.deleteDirectory(new File("./conf"));
                }
                // All logs
                if ((new File("./logs")).isDirectory())
                {
                    FileUtils.deleteDirectory(new File("./logs"));
                }
                // The war...
                if ((new File("./webapp")).isDirectory())
                {
                    FileUtils.deleteDirectory(new File("./webapp"));
                }
                // Where files created by payloads are stored
                File f = TestHelpers.node == null ? null : new File(TestHelpers.node.getDlRepo());
                if (f != null && f.isDirectory())
                {
                    FileUtils.cleanDirectory(new File(TestHelpers.node.getDlRepo()));
                }
                // Temp dir where files downloaded by the API are stored (supposed to be self-destructible file but anyway)
                if ((new File(System.getProperty("java.io.tmpdir") + "/jqm")).isDirectory())
                {
                    FileUtils.cleanDirectory(new File(System.getProperty("java.io.tmpdir") + "/jqm"));
                }
            }
            catch (IOException e)
            {
                // Nothing to do
            }
        }
    }

    public static String getLocalHostName()
    {
        String res = "localhost";
        try
        {
            String tmp = InetAddress.getLocalHost().getHostName();
            InetAddress[] adresses = InetAddress.getAllByName(tmp);
            for (InetAddress s : adresses)
            {
                if (s.isLoopbackAddress())
                {
                    res = "localhost"; // force true loopback in this case. We may have a hostname that resolves on a public interface with
                                       // a loopback adress...
                }
            }
        }
        catch (UnknownHostException e)
        {
            // We'll settle for localhost.
        }
        return res;
    }

    public static void setNodesLogLevel(String level, DbConn cnx)
    {
        cnx.runUpdate("node_update_all_log_level", level);
        cnx.commit();
    }

    public static void waitFor(long nbHistories, int timeoutMs, DbConn cnx)
    {
        Calendar start = Calendar.getInstance();
        while (getHistoryAllCount(cnx) < nbHistories && Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis() <= timeoutMs)
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    public static void waitForRunning(long nbJobInstances, int timeoutMs, DbConn cnx)
    {
        Calendar start = Calendar.getInstance();
        while (getQueueRunningCount(cnx) < nbJobInstances
                && Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis() <= timeoutMs)
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException e)
            {
            }
        }
    }

    public static int getHistoryAllCount(DbConn cnx)
    {
        return cnx.runSelectSingle("history_select_count_all", Integer.class);
    }

    public static int getQueueAllCount(DbConn cnx)
    {
        return cnx.runSelectSingle("ji_select_count_all", Integer.class);
    }

    public static int getQueueRunningCount(DbConn cnx)
    {
        return cnx.runSelectSingle("ji_select_count_running", Integer.class);
    }

    public static int getOkCount(DbConn cnx)
    {
        return cnx.runSelectSingle("history_select_count_ended", Integer.class);
    }

    /**
     * Ended JI, but not OK.
     */
    public static int getNonOkCount(DbConn cnx)
    {
        return cnx.runSelectSingle("history_select_count_notended", Integer.class);
    }

    public static boolean testOkCount(long theoreticalOkCount, DbConn cnx)
    {
        return getOkCount(cnx) == theoreticalOkCount;
    }

    public static int getDefaultQueueId(DbConn cnx)
    {
        return cnx.runSelectSingle("q_select_default", Integer.class);
    }

}
