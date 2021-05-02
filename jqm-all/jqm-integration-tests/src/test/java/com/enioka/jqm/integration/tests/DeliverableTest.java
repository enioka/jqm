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
import java.nio.charset.Charset;
import java.util.List;

import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // TODO
public class DeliverableTest extends JqmBaseTest
{
    @Before
    public void before() throws IOException
    {
        File jar = FileUtils.listFiles(new File("../jqm-ws/target/"), new String[] { "war" }, false).iterator().next();
        FileUtils.copyFile(jar, new File("./webapp/jqm-ws.war"));
    }

    /**
     * Retrieve all the files created by a job, with auth, without SSL
     */
    @Test
    public void testGetDeliverables() throws Exception
    {
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        Helpers.setSingleParam("enableWsApiSsl", "false", cnx);

        int id = JqmSimpleTest.create(cnx, "pyl.EngineApiSendDeliverable").addDefParameter("filepath", TestHelpers.node.getDlRepo())
                .addDefParameter("fileName", "jqm-test-deliverable1.txt").run(this);

        List<InputStream> tmp = jqmClient.getJobDeliverablesContent(id);
        // Assert.assertTrue(tmp.get(0).available() > 0);
        String res = IOUtils.toString(tmp.get(0), Charset.defaultCharset());
        Assert.assertTrue(res.startsWith("Hello World!"));

        tmp.get(0).close();
    }

    /**
     * Retrieve a remote file with authentication, without SSL.
     */
    @Test
    public void testGetOneDeliverableWithAuth() throws Exception
    {
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        Helpers.setSingleParam("enableWsApiSsl", "false", cnx);

        int jobId = JqmSimpleTest.create(cnx, "pyl.EngineApiSendDeliverable").addDefParameter("filepath", TestHelpers.node.getDlRepo())
                .addDefParameter("fileName", "jqm-test-deliverable2.txt").run(this);

        File f = new File(TestHelpers.node.getDlRepo() + "jqm-test-deliverable2.txt");
        Assert.assertEquals(false, f.exists()); // file should have been moved

        List<com.enioka.jqm.client.api.Deliverable> files = jqmClient.getJobDeliverables(jobId);
        Assert.assertEquals(1, files.size());

        InputStream tmp = jqmClient.getDeliverableContent(files.get(0));

        Assert.assertTrue(tmp.available() > 0);
        String res = IOUtils.toString(tmp, Charset.defaultCharset());
        Assert.assertTrue(res.startsWith("Hello World!"));

        tmp.close();
    }

    /**
     * Same as above, except authentication is disabled as well as SSL.
     */
    @Test
    public void testGetOneDeliverableWithoutAuth() throws Exception
    {
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "false", cnx);
        Helpers.setSingleParam("enableWsApiSsl", "false", cnx);

        int jobId = JqmSimpleTest.create(cnx, "pyl.EngineApiSendDeliverable").addDefParameter("filepath", TestHelpers.node.getDlRepo())
                .addDefParameter("fileName", "jqm-test-deliverable3.txt").run(this);

        File f = new File(TestHelpers.node.getDlRepo() + "jqm-test-deliverable3.txt");
        Assert.assertEquals(false, f.exists()); // file should have been moved

        List<com.enioka.jqm.client.api.Deliverable> files = jqmClient.getJobDeliverables(jobId);
        Assert.assertEquals(1, files.size());

        InputStream tmp = jqmClient.getDeliverableContent(files.get(0));
        Assert.assertTrue(tmp.available() > 0);
        String res = IOUtils.toString(tmp, Charset.defaultCharset());
        Assert.assertTrue(res.startsWith("Hello World!"));

        tmp.close();
    }

    /**
     * Retrieve a remote file with authentication, with SSL.
     */
    @Test
    public void testGetOneDeliverableWithAuthWithSsl() throws Exception
    {
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        Helpers.setSingleParam("enableWsApiSsl", "true", cnx);

        JqmClientFactory.resetClient(null);
        JqmClientFactory.setProperty("com.enioka.jqm.ws.truststoreFile", "./conf/trusted.jks");
        JqmClientFactory.setProperty("com.enioka.jqm.ws.truststorePass", "SuperPassword");

        int jobId = JqmSimpleTest.create(cnx, "pyl.EngineApiSendDeliverable").addDefParameter("filepath", TestHelpers.node.getDlRepo())
                .addDefParameter("fileName", "jqm-test-deliverable4.txt").run(this);

        File f = new File(TestHelpers.node.getDlRepo() + "jqm-test-deliverable4.txt");
        Assert.assertEquals(false, f.exists()); // file should have been moved

        List<com.enioka.jqm.client.api.Deliverable> files = jqmClient.getJobDeliverables(jobId);
        Assert.assertEquals(1, files.size());

        InputStream tmp = jqmClient.getDeliverableContent(files.get(0));
        Assert.assertTrue(tmp.available() > 0);
        String res = IOUtils.toString(tmp, Charset.defaultCharset());
        Assert.assertTrue(res.startsWith("Hello World!"));

        tmp.close();
    }

    /**
     * This test is DB only - no simple service use
     */
    @Test
    public void testGetAllDeliverables() throws Exception
    {
        int jobId = JqmSimpleTest.create(cnx, "pyl.EngineApiSendDeliverable").addDefParameter("filepath", TestHelpers.node.getDlRepo())
                .addDefParameter("fileName", "jqm-test-deliverable5.txt").run(this);

        List<com.enioka.jqm.client.api.Deliverable> tmp = jqmClient.getJobDeliverables(jobId);
        Assert.assertEquals(1, tmp.size());
    }
}
