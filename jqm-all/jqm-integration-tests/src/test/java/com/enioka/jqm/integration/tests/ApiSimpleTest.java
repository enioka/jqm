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
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ops4j.pax.exam.Option;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.State;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

/**
 * Tests of the simple web API.
 *
 */
public class ApiSimpleTest extends JqmBaseTest
{
    private int port;

    @Override
    protected Option[] moreOsgiconfig()
    {
        return webConfig();
    }

    @Before
    public void before() throws IOException
    {
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "false", cnx);

        File jar = FileUtils.listFiles(new File("../jqm-ws/target/"), new String[] { "war" }, false).iterator().next();
        FileUtils.copyFile(jar, new File("./webapp/jqm-ws.war"));

        addAndStartEngine();

        serviceWaiter.waitForService("[com.enioka.jqm.ws.api.ServiceSimple]");
        serviceWaiter.waitForService("[javax.servlet.Servlet]"); // HTTP whiteboard
        serviceWaiter.waitForService("[javax.servlet.Servlet]"); // JAX-RS whiteboard

        port = Node.select_single(cnx, "node_select_by_id", TestHelpers.node.getId()).getPort();
        jqmlogger.info("Jetty port seen by client is {}", port);
    }

    @Test
    public void testHttpEnqueue() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.EngineApiSend3Msg", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip,
                42, "Marsu-Application", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        HttpPost post = new HttpPost("http://" + TestHelpers.node.getDns() + ":" + port + "/ws/simple/ji");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("applicationname", "Marsu-Application"));
        nvps.add(new BasicNameValuePair("user", "testuser"));
        nvps.add(new BasicNameValuePair("module", "testuser"));
        nvps.add(new BasicNameValuePair("parameterNames", "arg"));
        nvps.add(new BasicNameValuePair("parameterValues", "overridevalue"));
        nvps.add(new BasicNameValuePair("parameterNames", "arg2"));
        nvps.add(new BasicNameValuePair("parameterValues", "newvalue2"));
        post.setEntity(new UrlEncodedFormEntity(nvps));

        HttpClient client = HttpClients.createDefault();
        HttpResponse res = client.execute(post);

        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String result;
        try (InputStream in = entity.getContent(); StringWriter writer = new StringWriter();)
        {
            IOUtils.copy(in, writer, Charset.defaultCharset());
            result = writer.toString();
        }

        Integer jid = 0;
        try
        {
            jid = Integer.parseInt(result);
        }
        catch (Exception e)
        {
            Assert.fail("result was not an integer " + e.getMessage());
        }

        TestHelpers.waitFor(1, 10000, cnx);

        // Check run is OK & parameters have been correctly processed
        JobInstance ji = JqmClientFactory.getClient().getJob(jid);
        Assert.assertEquals(com.enioka.jqm.client.api.State.ENDED, ji.getState());
        Assert.assertEquals(2, ji.getParameters().size());
        Assert.assertEquals("newvalue2", ji.getParameters().get("arg2"));
        Assert.assertEquals("overridevalue", ji.getParameters().get("arg"));
    }

    @Test
    public void testHttpStatus() throws Exception
    {
        CreationTools.createJobDef(null, true, "pyl.EngineApiSend3Msg", null, "jqm-tests/jqm-test-pyl/target/test.jar", TestHelpers.qVip,
                42, "Marsu-Application", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);

        HttpPost post = new HttpPost("http://" + TestHelpers.node.getDns() + ":" + port + "/ws/simple/ji");
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("applicationname", "Marsu-Application"));
        nvps.add(new BasicNameValuePair("user", "testuser"));
        nvps.add(new BasicNameValuePair("module", "testuser"));
        nvps.add(new BasicNameValuePair("param_1", "arg"));
        nvps.add(new BasicNameValuePair("paramvalue_1", "newvalue"));
        post.setEntity(new UrlEncodedFormEntity(nvps));

        HttpClient client = HttpClients.createDefault();
        HttpResponse res = client.execute(post);

        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        HttpEntity entity = res.getEntity();
        String result;
        try (InputStream in = entity.getContent(); StringWriter writer = new StringWriter();)
        {
            IOUtils.copy(in, writer, Charset.defaultCharset());
            result = writer.toString();
        }

        Integer jid = 0;
        try
        {
            jid = Integer.parseInt(result);
        }
        catch (Exception e)
        {
            Assert.fail("result was not an integer " + e.getMessage());
        }

        TestHelpers.waitFor(1, 10000, cnx);

        HttpGet rq = new HttpGet("http://" + TestHelpers.node.getDns() + ":" + port + "/ws/simple/status?id=" + jid);
        res = client.execute(rq);
        Assert.assertEquals(200, res.getStatusLine().getStatusCode());

        entity = res.getEntity();
        State currentState;
        try (InputStream in = entity.getContent(); StringWriter writer = new StringWriter();)
        {
            IOUtils.copy(in, writer, Charset.defaultCharset());
            currentState = State.valueOf(writer.toString());
        }

        Assert.assertEquals(State.ENDED, currentState);
    }
}
