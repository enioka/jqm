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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enioka.jqm.client.api.Deliverable;
import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.model.JobDef.PathType;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class ShellRunnerTest extends JqmBaseTest
{
    @Test
    public void testDefaultShellCommandBasic()
    {
        AssumeWindows();

        CreationTools.createJobDef("test job", true, "none", new HashMap<>(), "set", TestHelpers.qNormal, 0, "TestApp1", null, "module1",
                "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testDefaultShellCommand()
    {
        // Also tests env vars.
        String command1 = "set | grep JQM_JD_APPLICATION_NAME";
        String command2 = "false"; // should fail
        if (onWindows())
        {
            command1 = "set | findstr JQM_JD_APPLICATION_NAME";
            command2 = "set | findstr JQM_XXXX";
        }

        CreationTools.createJobDef("test job", true, "none", new HashMap<>(), command1, TestHelpers.qNormal, 0, "TestApp1", null, null,
                "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        CreationTools.createJobDef("failing test job", true, "none", new HashMap<>(), command2, TestHelpers.qNormal, 0, "TestApp2", null,
                "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();
        jqmClient.newJobRequest("TestApp2", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(2, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testDefaultShellCommandEscaping()
    {
        String command1 = "/bin/sh -c \"echo 'aa bb'\""; // this will be called within another /bin/sh!
        if (onWindows())
        {
            command1 = "cmd.exe /C echo 'aa bb'";
        }

        CreationTools.createJobDef("test job", true, "none", new HashMap<>(), command1, TestHelpers.qNormal, 0, "TestApp1", null, "module1",
                "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testPowerShellCommand()
    {
        AssumeWindows();

        String command1 = "echo 'aa' ; echo 'bb';";

        CreationTools.createJobDef("test job", true, "none", new HashMap<>(), command1, TestHelpers.qNormal, 0, "TestApp1", null, "module1",
                "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.POWERSHELLCOMMAND);
        jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testSimpleExe()
    {
        String command1 = "/bin/sh";
        Map<String, String> args = new HashMap<>();
        args.put("01", "-c");
        args.put("02", "echo 'aa bb'");

        if (onWindows())
        {
            command1 = "cmd.exe";
            args.put("01", "/C");
            args.put("02", "echo aa bb");
            args.put("00", "/D"); // Note the order - should arrive first in result command
        }

        CreationTools.createJobDef("test job", true, "none", args, command1, TestHelpers.qNormal, 0, "TestApp1", null, "module1", "kw1",
                "kw2", null, false, cnx, null, false, null, false, PathType.DIRECTEXECUTABLE);
        jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testKill()
    {
        if (onWindows())
        {
            // We explicitely start a sub shell here so as to have a process tree powershell-> powershell.
            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), "powershell.exe -Command 'Start-Sleep 3600'",
                    TestHelpers.qNormal, 0, "TestApp1", null, "module1", "kw1", "kw2", null, false, cnx, null, false, null, false,
                    PathType.POWERSHELLCOMMAND);
        }
        else
        {
            // For Linux, sleep is a process, not a command, so we have a shell->sleep tree.
            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), "sleep 3600", TestHelpers.qNormal, 0, "TestApp1", null,
                    "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        }

        int i = jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();
        Helpers.setSingleParam("internalPollingPeriodMs", "500", cnx);

        addAndStartEngine();
        TestHelpers.waitForRunning(1, 20000, cnx);
        jqmClient.killJob(i);
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(0, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(1, TestHelpers.getNonOkCount(cnx));
    }

    /**
     * In this test, a first shell command calls the simple web API with JQM-provided parameters to enqueue a second job (a simple echo).
     */
    @Test
    public void testApiCallFromShellJob() throws IOException
    {
        // Start the WS
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        File jar = FileUtils.listFiles(new File("../jqm-ws/target/"), new String[] { "war" }, false).iterator().next();
        FileUtils.copyFile(jar, new File("./webapp/jqm-ws.war"));

        // Normal test
        if (onWindows())
        {
            CreationTools.createJobDef("test job 2", true, "none", new HashMap<>(), "echo aa", TestHelpers.qNormal, 0, "TestApp2", null,
                    "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);

            String script = "ls env: ; $c = New-Object System.Management.Automation.PSCredential ($env:JQM_API_LOGIN, (ConvertTo-SecureString $env:JQM_API_PASSWORD -AsPlainText -Force) ) ;"
                    + "Invoke-webrequest $env:JQM_API_LOCAL_URL/ws/simple/ji -Method Post -Body @{applicationname='TestApp2';parentid=$env:JQM_JI_ID}  -credential $c -UseBasicParsing";

            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), script, TestHelpers.qNormal, 0, "TestApp1", null,
                    "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.POWERSHELLCOMMAND);
        }
        else
        {
            CreationTools.createJobDef("test job 2", true, "none", new HashMap<>(), "echo 'aa'", TestHelpers.qNormal, 0, "TestApp2", null,
                    "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);

            String script = "curl --user \"${JQM_API_LOGIN}:${JQM_API_PASSWORD}\" --url \"${JQM_API_LOCAL_URL}/ws/simple/ji\" -d \"applicationname=TestApp2&parentid=${JQM_JI_ID}\" -H 'Content-Type: application/x-www-form-urlencoded' -s ";

            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), script, TestHelpers.qNormal, 0, "TestApp1", null,
                    "module1", "kw1", "kw2", null, false, cnx, null, false, null, false, PathType.DEFAULTSHELLCOMMAND);
        }

        int i = jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();

        addAndStartEngine();
        TestHelpers.waitFor(2, 20000, cnx);

        Assert.assertEquals(2, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));

        List<JobInstance> jis = jqmClient.newQuery().setParentId(i).invoke();
        Assert.assertEquals(1, jis.size());
    }

    @Test
    public void testShellDelivery()
    {
        if (onWindows())
        {
            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), "echo 'toto' > $env:JQM_JI_DELIVERY_DIR/test.txt",
                    TestHelpers.qNormal, 0, "TestApp1", null, "module1", "kw1", "kw2", null, false, cnx, null, false, null, false,
                    PathType.POWERSHELLCOMMAND);
        }
        else
        {
            // For Linux, sleep is a process, not a command, so we have a shell->sleep tree.
            CreationTools.createJobDef("test job", true, "none", new HashMap<>(), "echo 'toto' > $JQM_JI_DELIVERY_DIR/test.txt",
                    TestHelpers.qNormal, 0, "TestApp1", null, "module1", "kw1", "kw2", null, false, cnx, null, false, null, false,
                    PathType.DEFAULTSHELLCOMMAND);
        }

        int i = jqmClient.newJobRequest("TestApp1", "TestUser").enqueue();
        Helpers.setSingleParam("internalPollingPeriodMs", "500", cnx);

        addAndStartEngine();
        TestHelpers.waitFor(1, 20000, cnx);

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));

        List<Deliverable> deliverables = jqmClient.getJobDeliverables(i);
        Assert.assertEquals(1, deliverables.size());
        Assert.assertEquals("test.txt", deliverables.get(0).getFileFamily());
    }
}
