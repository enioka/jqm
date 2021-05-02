package com.enioka.jqm.tools;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import com.enioka.jqm.client.api.JqmInvalidRequestException;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.integration.tests.ClientApiTest;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.test.helpers.TestHelpers;

/**
 * Run all the client tests, but this time with a WS client.
 */
public class WsClientClearTest extends ClientApiTest
{
    private Node n;

    @Before
    public void before() throws IOException
    {
        File jar = FileUtils.listFiles(new File("../jqm-ws/target/"), new String[] { "war" }, false).iterator().next();
        FileUtils.copyFile(jar, new File("./webapp/jqm-ws.war"));

        // Create a node without pollers which will host all WS calls.
        n = Node.create(cnx, "wsnode", 0, "./target/outputfiles/", "./../", "./target/tmp", TestHelpers.getLocalHostName(), "DEBUG");
        Helpers.setSingleParam("disableWsApi", "false", cnx);
        Helpers.setSingleParam("enableWsApiAuth", "true", cnx);
        Helpers.setSingleParam("enableWsApiSsl", "false", cnx);
        Helpers.setSingleParam("logFilePerLaunch", "both", cnx);
        cnx.runUpdate("node_update_all_disable_all_ws");
        cnx.runUpdate("node_update_enable_ws_by_id", n.getId());
        cnx.commit();

        addAndStartEngine("wsnode");
        n = Node.select_single(cnx, "node_select_by_key", "wsnode");

        // Set client properties to use this node.
        Properties p = new Properties();
        p.put("com.enioka.jqm.ws.url",
                "http://" + (n.getDns().equals("0.0.0.0") ? "localhost" : n.getDns()) + ":" + n.getPort() + "/ws/client");
        p.put("com.enioka.jqm.ws.login", "test");
        p.put("com.enioka.jqm.ws.password", "testpassword");
        JqmClientFactory.setProperties(p);

        Helpers.createUserIfMissing(cnx, "test", "testpassword", "test user for WS Junit tests", "client power user");
        cnx.commit();
    }

    @Test(expected = JqmInvalidRequestException.class)
    public void testBug292()
    {
        // TODO: jersey !
        JqmClientFactory.getClient().newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(false).invoke();
    }
}
