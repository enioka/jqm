package com.enioka.jqm.service;

import java.util.Calendar;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;

import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.engine.JqmEngineHandler;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;

public class EngineCallback implements JqmEngineHandler
{
    // private static Logger jqmlogger = Logger.getLogger(EngineCallback.class);

    private JettyServer server = null;
    private DirectoryScanner scanner = null;
    private String logLevel = "INFO";
    private String nodePrms = null;
    private Calendar latestJettyRestart = Calendar.getInstance();
    private boolean oneLogPerLaunch = false;
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

        /*
         * TODO // Log multicasting (& log4j stdout redirect) String gp1 = GlobalParameter.getParameter(cnx, "logFilePerLaunch", "true"); if
         * ("true".equals(gp1) || "both".equals(gp1)) { oneLogPerLaunch = true; Logger root = (Logger)
         * LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME); RollingFileAppender a = (RollingFileAppender) root.getAppender("rollingfile");
         * MultiplexPrintStream s = new MultiplexPrintStream(System.out, FilenameUtils.getFullPath(a.getFile()), "both".equals(gp1));
         * System.setOut(s); ((ConsoleAppender) root.getAppender("consoleAppender")).setTarget("System.out"); s = new
         * MultiplexPrintStream(System.err, FilenameUtils.getFullPath(a.getFile()), "both".equals(gp1)); System.setErr(s); }
         */

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
    {
        if (oneLogPerLaunch)
        {
            String fileName = StringUtils.leftPad("" + job.getId(), 10, "0");
            MultiplexPrintStream mps = (MultiplexPrintStream) System.out;
            mps.registerThread(String.valueOf(fileName + ".stdout.log"));
            mps = (MultiplexPrintStream) System.err;
            mps.registerThread(String.valueOf(fileName + ".stderr.log"));
        }
    }

    @Override
    public void onJobInstanceDone(JobInstance ji)
    {
        if (System.out instanceof MultiplexPrintStream)
        {
            MultiplexPrintStream mps = (MultiplexPrintStream) System.out;
            mps.unregisterThread();
            mps = (MultiplexPrintStream) System.err;
            mps.unregisterThread();
        }

    }

    @Override
    public void onNodeStarting(String nodeName)
    {
        CommonService.setLogFileName(nodeName);
    }
}
