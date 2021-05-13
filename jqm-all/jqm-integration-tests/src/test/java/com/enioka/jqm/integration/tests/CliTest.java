package com.enioka.jqm.integration.tests;

import javax.inject.Inject;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.NodeDto;
import com.enioka.jqm.cli.bootstrap.CommandLine;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.RUser;
import com.enioka.jqm.test.helpers.CreationTools;
import com.enioka.jqm.test.helpers.TestHelpers;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class CliTest extends JqmBaseTest
{
    @Inject
    CommandLine parser;

    @Test
    public void testCliChangeUser()
    {
        Helpers.updateConfiguration(cnx);
        parser.runOsgiCommand(new String[] { "Reset-User", "--login", "myuser", "-p", "mypassword", "--roles", "administrator", "client" });

        RUser u = RUser.selectlogin(cnx, "myuser");

        Assert.assertEquals(2, u.getRoles(cnx).size());
        boolean admin = false, client = false;
        for (RRole r : u.getRoles(cnx))
        {
            if (r.getName().equals("administrator"))
            {
                admin = true;
            }
            if (r.getName().equals("client"))
            {
                client = true;
            }
        }
        Assert.assertTrue(client && admin);

        parser.runOsgiCommand(new String[] { "Reset-User", "--login", "myuser", "--password", "mypassword", "--roles", "administrator" });
        Assert.assertEquals(1, u.getRoles(cnx).size());

        parser.runOsgiCommand(
                new String[] { "Reset-User", "--login", "myuser", "-p", "mypassword", "--roles", "administrator", "config admin" });
        Assert.assertEquals(2, u.getRoles(cnx).size());
    }

    @Test
    @Ignore
    public void testSingleLauncher() throws Exception
    {
        CreationTools.createJobDef(null, true, "App", null, "jqm-tests/jqm-test-datetimemaven/target/test.jar", TestHelpers.qVip, 42,
                "MarsuApplication", null, "Franquin", "ModuleMachin", "other", "other", true, cnx);
        int i = jqmClient.newJobRequest("MarsuApplication", "TestUser").enqueue();
        cnx.runUpdate("ji_update_status_by_id", TestHelpers.node.getId(), i);
        cnx.runUpdate("debug_jj_update_node_by_id", TestHelpers.node.getId(), i);
        cnx.commit();

        parser.runOsgiCommand(new String[] { "Start-Single", "--id", String.valueOf(i) });

        // This is not really a one shot JVM, so let's reset log4j
        /*
         * TODO : don't know what to do here LogManager.resetConfiguration();
         * PropertyConfigurator.configure(this.getClass().getClassLoader().getResource("log4j.properties"));
         */

        Assert.assertEquals(1, TestHelpers.getOkCount(cnx));
        Assert.assertEquals(0, TestHelpers.getNonOkCount(cnx));
    }

    @Test
    public void testTemplate() throws Exception
    {
        NodeDto template = MetaService.getNode(cnx, TestHelpers.nodeMix.getId());
        template.setPort(123);
        MetaService.upsertNode(cnx, template);
        cnx.commit();

        NodeDto target = MetaService.getNode(cnx, TestHelpers.node.getId());
        Assert.assertEquals(3, MetaService.getNodeQueueMappings(cnx, target.getId()).size());

        // Capital letter -> should be ignored.
        parser.runOsgiCommand(
                new String[] { "Install-NodeTemPlate", "-t", TestHelpers.nodeMix.getName(), "-n", TestHelpers.node.getName() });

        target = MetaService.getNode(cnx, TestHelpers.node.getId());

        Assert.assertEquals(template.getPort(), target.getPort());
        Assert.assertEquals(1, MetaService.getNodeQueueMappings(cnx, target.getId()).size());
    }
}
