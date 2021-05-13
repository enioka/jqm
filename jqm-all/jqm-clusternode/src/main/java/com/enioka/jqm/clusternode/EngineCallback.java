package com.enioka.jqm.clusternode;

import java.util.Calendar;

import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.engine.JqmEngineHandler;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;

import org.osgi.service.cm.ConfigurationAdmin;

public class EngineCallback implements JqmEngineHandler
{
    // private static Logger jqmlogger = Logger.getLogger(EngineCallback.class);

    private JettyServer server = null;
    private DirectoryScanner scanner = null;
    private String logLevel = "INFO";
    private String nodePrms = null;
    private Calendar latestJettyRestart = Calendar.getInstance();
    private ConfigurationAdmin adminService;

    public EngineCallback(ConfigurationAdmin adminService)
    {
        this.adminService = adminService;
    }

    @Override
    public void onConfigurationChanged(Node node)
    {
        // Log level changes.
        if (!this.logLevel.equals(node.getRootLogLevel()))
        {
            this.logLevel = node.getRootLogLevel();
            CommonService.setLogLevel(this.logLevel);
        }

        // Jetty restart. Conditions are:
        // * some parameters (such as security parameters) have changed
        // * node parameter change such as start or stop an API.
        Calendar bflkpm = Calendar.getInstance();
        String np = node.getDns() + node.getPort() + node.getLoadApiAdmin() + node.getLoadApiClient() + node.getLoapApiSimple();
        if (nodePrms == null)
        {
            nodePrms = np;
        }
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            int i = cnx.runSelectSingle("globalprm_select_count_modified_jetty", Integer.class, latestJettyRestart);
            if (i > 0 || !np.equals(nodePrms))
            {
                this.server.start(node, cnx, adminService);
                latestJettyRestart = bflkpm;
                nodePrms = np;
            }
        }
    }

    @Override
    public void onNodeConfigurationRead(Node node)
    {
        DbConn cnx = Helpers.getNewDbSession();

        // Main log levels comes from configuration
        CommonService.setLogLevel(node.getRootLogLevel());
        this.logLevel = node.getRootLogLevel();

        // Jetty
        this.server = new JettyServer();
        this.server.start(node, cnx, adminService);

        // Deployment scanner
        String gp2 = GlobalParameter.getParameter(cnx, "directoryScannerRoot", "");
        if (!gp2.isEmpty())
        {
            scanner = new DirectoryScanner(gp2, node);
            (new Thread(scanner)).start();
        }

        cnx.close();
    }

    @Override
    public void onNodeStopped()
    {
        this.server.stop(adminService);
        if (this.scanner != null)
        {
            this.scanner.stop();
        }
    }

    @Override
    public void onNodeStarted()
    {
        // Nothing done by default.
    }

    @Override
    public void onJobInstancePreparing(JobInstance job)
    {}

    @Override
    public void onJobInstanceDone(JobInstance ji)
    {}

    @Override
    public void onNodeStarting(String nodeName)
    {
        CommonService.setLogFileName(nodeName);
    }
}
