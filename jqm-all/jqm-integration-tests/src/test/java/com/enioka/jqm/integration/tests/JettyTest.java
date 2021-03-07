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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;

import com.enioka.jqm.api.client.core.JobRequest;
import com.enioka.jqm.api.client.core.JqmClientFactory;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.pki.JdbcCa;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.ops4j.pax.exam.Option;

public class JettyTest extends JqmBaseTest
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
        File jar = FileUtils.listFiles(new File("../jqm-ws/target/"), new String[] { "war" }, false).iterator().next();
        FileUtils.copyFile(jar, new File("./webapp/jqm-ws.war"));
    }

    private void waitStartup()
    {
        serviceWaiter.waitForService("[com.enioka.jqm.ws.api.ServiceSimple]");
        serviceWaiter.waitForService("[javax.servlet.Servlet]"); // HTTP whiteboard
        serviceWaiter.waitForService("[javax.servlet.Servlet]"); // JAX-RS whiteboard

        port = Node.select_single(cnx, "node_select_by_id", TestHelpers.node.getId()).getPort();
        jqmlogger.info("Jetty port seen by client is {}", port);
    }

    @Test
    public void testSslStartup()
    {
        Helpers.setSingleParam("enableWsApiSsl", "true", cnx);
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "false", cnx);

        addAndStartEngine();
        waitStartup();
    }

    @Test
    public void testSslServices() throws Exception
    {
        Helpers.setSingleParam("enableWsApiSsl", "true", cnx);
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "false", cnx);

        addAndStartEngine();
        waitStartup();

        // Launch a job so as to be able to query its status later
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        JobRequest j = new JobRequest("MarsuApplication", "TestUser");
        int i = JqmClientFactory.getClient().enqueue(j);
        TestHelpers.waitFor(1, 10000, cnx);

        // HTTPS client - with
        KeyStore trustStore = KeyStore.getInstance("JKS");
        FileInputStream instream = new FileInputStream(new File("./conf/trusted.jks"));
        try
        {
            trustStore.load(instream, "SuperPassword".toCharArray());
        }
        finally
        {
            instream.close();
        }

        SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore, null).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1.2" }, null,
                new DefaultHostnameVerifier());

        CloseableHttpClient cl = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        int port = Node.select_single(cnx, "node_select_by_id", TestHelpers.node.getId()).getPort();
        HttpUriRequest rq = new HttpGet("https://" + TestHelpers.node.getDns() + ":" + port + "/ws/simple/status?id=" + i);
        jqmlogger.debug(rq.getURI().toString());
        CloseableHttpResponse rs = cl.execute(rq);
        Assert.assertEquals(200, rs.getStatusLine().getStatusCode());
        jqmlogger.debug(IOUtils.toString(rs.getEntity().getContent(), StandardCharsets.UTF_8));

        rs.close();
        cl.close();
    }

    @Test
    public void testSslClientCert() throws Exception
    {
        Helpers.setSingleParam("enableWsApiSsl", "true", cnx);
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        Helpers.createUserIfMissing(cnx, "testuser", null, "test user", "client read only");
        cnx.commit();

        addAndStartEngine();
        waitStartup();

        // Launch a job so as to be able to query its status later
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        JobRequest j = new JobRequest("MarsuApplication", "TestUser");
        int i = JqmClientFactory.getClient().enqueue(j);
        TestHelpers.waitFor(1, 10000, cnx);

        // Server auth against trusted CA root certificate
        KeyStore trustStore = KeyStore.getInstance("JKS");
        FileInputStream instream = new FileInputStream(new File("./conf/trusted.jks"));
        try
        {
            trustStore.load(instream, "SuperPassword".toCharArray());
        }
        finally
        {
            instream.close();
        }

        // Client auth
        JdbcCa.prepareClientStore(cnx, "CN=testuser", "./conf/client.pfx", "SuperPassword", "client-cert", "./conf/client.cer");
        KeyStore clientStore = KeyStore.getInstance("PKCS12");
        instream = new FileInputStream(new File("./conf/client.pfx"));
        try
        {
            clientStore.load(instream, "SuperPassword".toCharArray());
        }
        finally
        {
            instream.close();
        }

        SSLContext sslcontext = SSLContexts.custom().loadTrustMaterial(trustStore, null)
                .loadKeyMaterial(clientStore, "SuperPassword".toCharArray()).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1.2" }, null,
                new DefaultHostnameVerifier());

        CloseableHttpClient cl = HttpClients.custom().setSSLSocketFactory(sslsf).build();

        int port = Node.select_single(cnx, "node_select_by_id", TestHelpers.node.getId()).getPort();
        HttpUriRequest rq = new HttpGet("https://" + TestHelpers.node.getDns() + ":" + port + "/ws/simple/status?id=" + i);
        jqmlogger.debug(rq.getURI().toString());
        CloseableHttpResponse rs = cl.execute(rq);
        jqmlogger.debug(IOUtils.toString(rs.getEntity().getContent(), StandardCharsets.UTF_8));
        Assert.assertEquals(200, rs.getStatusLine().getStatusCode());

        rs.close();
        cl.close();

    }
}
