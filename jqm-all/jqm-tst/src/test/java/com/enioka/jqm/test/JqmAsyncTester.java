package com.enioka.jqm.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.enioka.jqm.client.api.Deliverable;
import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.api.JqmInvalidRequestException;
import com.enioka.jqm.client.api.State;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.engine.JqmEngineFactory;
import com.enioka.jqm.engine.JqmEngineOperations;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;

import org.apache.commons.io.FilenameUtils;
import org.hsqldb.Server;
import org.hsqldb.jdbc.JDBCDataSource;

/**
 * An asynchronous tester for JQM payloads. It allows to configure and start one or more embedded JQM engines and run payloads against them.
 * It is most suited for integration tests.<br>
 * <br>
 * It starts full JQM nodes running on an in-memory embedded database. They are started with all web API disabled.<br>
 * The user should handle interactions with the nodes through the normal client APIs. See {@link JqmClient} and {@link JqmClientFactory}. As
 * the web services are not loaded, the file retrieval methods of these APIs will not work, so the tester provides a
 * {@link #getDeliverableContent(Deliverable)} method to compensate. The tester also provides a few helper methods (accelerators) that
 * encapsulate the client API.<br>
 *
 * If using resources (JNDI), they must be put inside a resource.xml file at the root of class loader search.<br>
 * Note that tester instances are not thread safe.
 */
public class JqmAsyncTester
{
    private Map<String, JqmEngineOperations> engines = new HashMap<>();
    private Map<String, Node> nodes = new HashMap<>();
    private Map<String, Integer> queues = new HashMap<>();

    private Db db = null;
    private DbConn cnx = null;
    private Server s = null;

    private boolean hasStarted = false;
    private String logLevel = "DEBUG";
    private boolean oneQueueDeployed = false;

    ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTION
    ///////////////////////////////////////////////////////////////////////////

    public JqmAsyncTester()
    {
        // Ext dir
        File extDir = new File("./ext");
        if (!extDir.exists() && !extDir.mkdir())
        {
            throw new RuntimeException(new IOException("./ext directory does not exist and cannot create it"));
        }

        s = Common.createHsqlServer();
        s.start();

        JDBCDataSource ds = new JDBCDataSource();
        ds.setDatabase("jdbc:hsqldb:mem:" + s.getDatabaseName(0, true));
        db = new Db(ds, true);
        cnx = db.getConn();

        Properties p2 = Common.dbProperties(s);
        p2.put("com.enioka.jqm.jdbc.contextobject", db);
        JqmClientFactory.setProperties(p2);
        JqmEngineFactory.setDatasource(db);
        JqmEngineFactory.initializeMetadata();
        cnx.runUpdate("dp_delete_all");
        cnx.runUpdate("q_delete_all");
        cnx.commit();

        // Needed parameters
        addGlobalParameter("defaultConnection", "");
        addGlobalParameter("disableWsApi", "true");
        addGlobalParameter("logFilePerLaunch", "false");

        // Prepare DB

    }

    /**
     * Equivalent to simply calling the constructor. Present for consistency.
     */
    public static JqmAsyncTester create()
    {
        return new JqmAsyncTester();
    }

    /**
     * A helper method which creates a preset environment with a single node called 'node1' and a single queue named 'queue1' being polled
     * every 100ms by the node with at most 10 parallel running job instances..
     */
    public static JqmAsyncTester createSingleNodeOneQueue()
    {
        return new JqmAsyncTester().addNode("node1").addQueue("queue1").deployQueueToNode("queue1", 10, 100, "node1");
    }

    ///////////////////////////////////////////////////////////////////////////
    // TEST PREPARATION
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Create a new node. It is not started by this method.<br>
     * This must be called before starting the tester.
     *
     * @param nodeName
     *            the name of the node. Must be unique.
     */
    public JqmAsyncTester addNode(String nodeName)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        File resDirectoryPath = Common.createTempDirectory();

        Node node = Node.create(cnx, nodeName, 12, resDirectoryPath.getAbsolutePath(), ".", resDirectoryPath.getAbsolutePath(), "test",
                logLevel);
        cnx.commit();
        nodes.put(nodeName, node);

        setNodesLogLevel(logLevel);

        return this;
    }

    /**
     * Changes the log level of existing and future nodes.
     *
     * @param level
     *            TRACE, DEBUG, INFO, WARNING, ERROR (or anything, which is interpreted as INFO)
     */
    public JqmAsyncTester setNodesLogLevel(String level)
    {
        logLevel = level;
        cnx.runUpdate("node_update_all_log_level", level);
        cnx.commit();
        return this;
    }

    /**
     * Create a new queue. After creation, it is not polled by any node - see {@link #deployQueueToNode(String, int, int, String...)} for
     * this.<br>
     * The first queue created is considered to be the default queue.<br>
     * This must be called before starting the engines.
     *
     * @param name
     *            must be unique.
     */
    public JqmAsyncTester addQueue(String name)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        int q = Queue.create(cnx, name, "test queue", queues.size() == 0);
        cnx.commit();

        queues.put(name, q);

        return this;
    }

    /**
     * Set one or more nodes to poll a queue for new job instances.<br>
     * This must be called before starting the engines.
     */
    public JqmAsyncTester deployQueueToNode(String queueName, int maxJobsRunning, int pollingIntervallMs, String... nodeName)
    {
        if (hasStarted)
        {
            throw new IllegalStateException("tester has already started");
        }

        for (String name : nodeName)
        {
            DeploymentParameter.create(cnx, nodes.get(name).getId(), maxJobsRunning, pollingIntervallMs, queues.get(queueName));
            cnx.commit();
        }

        oneQueueDeployed = true;
        return this;
    }

    /**
     * Set or update a global parameter.
     */
    public void addGlobalParameter(String key, String value)
    {
        GlobalParameter.setParameter(cnx, key, value);
        cnx.commit();
    }

    /**
     * Add a new job definition (see documentation) to the database.<br>
     * This can be called at any time (even after engine(s) start).
     */
    public JqmAsyncTester addJobDefinition(TestJobDefinition description)
    {
        Common.createJobDef(cnx, description, queues);
        cnx.commit();
        return this;
    }

    /**
     * A helper method to create a job definition from a class <strong>which is present inside the current class path</strong>.<br>
     * The job description and name will be the class name (simple name, not the fully qualified name).<br>
     * If you need further customisation, directly create your {@link TestJobDefinition} and call
     * {@link #addJobDefinition(TestJobDefinition)} instead of using this method.
     *
     * @param classToRun
     *            a class present inside the class path which should be launched by JQM.
     * @return the tester itself to allow fluid API behaviour.
     */
    public JqmAsyncTester addSimpleJobDefinitionFromClasspath(Class<? extends Object> classToRun)
    {
        TestJobDefinition jd = TestJobDefinition.createFromClassPath(classToRun.getSimpleName(), "test payload " + classToRun.getName(),
                classToRun);
        return this.addJobDefinition(jd);
    }

    /**
     * A helper method to create a job definition from a class <strong>which is present inside an existing jar file</strong>.<br>
     * The job description and name will be identical<br>
     * If you need further customisation, directly create your {@link TestJobDefinition} and call
     * {@link #addJobDefinition(TestJobDefinition)} instead of using this method.
     *
     * @param name
     *            name of the new job definition (as used in the enqueue methods)
     * @param className
     *            the full canonical name of the the class to run inside the jar
     * @param jarPath
     *            path to the jar. Relative to current directory.
     * @return the tester itself to allow fluid API behaviour.
     */
    public JqmAsyncTester addSimpleJobDefinitionFromLibrary(String name, String className, String jarPath)
    {
        TestJobDefinition jd = TestJobDefinition.createFromJar(name, name, className, jarPath);
        return this.addJobDefinition(jd);
    }

    ///////////////////////////////////////////////////////////////////////////
    // DURING TEST
    ///////////////////////////////////////////////////////////////////////////

    /**
     * This actually starts the different engines configured with {@link #addNode(String)}.<br>
     * This can usually only be called once (it can actually be called again but only after calling {@link #stop()}).
     */
    public JqmAsyncTester start()
    {
        if (hasStarted)
        {
            throw new IllegalStateException("cannot start twice");
        }
        if (nodes.isEmpty())
        {
            throw new IllegalStateException("no engines defined");
        }
        if (queues.isEmpty())
        {
            throw new IllegalStateException("no queues defined");
        }
        if (!oneQueueDeployed)
        {
            throw new IllegalStateException("no queue was ever deployed to any node");
        }
        hasStarted = true;
        for (Node n : nodes.values())
        {
            engines.put(n.getName(), JqmEngineFactory.startEngine(n.getName(), null));
        }
        return this;
    }

    /**
     * Helper method to enqueue a new launch request. Simple JqmClientFactory.getClient().enqueue wrapper.
     *
     * @return the request ID.
     */
    public int enqueue(String name)
    {
        return JqmClientFactory.getClient().newJobRequest(name, "test").enqueue();
    }

    /**
     * Wait for a given amount of ended job instances (OK or KO).
     *
     * @param nbResult
     *            the expected result count
     * @param timeoutMs
     *            give up after this (throws a RuntimeException)
     * @param waitAdditionalMs
     *            after reaching the expected nbResult count, wait a little more (for example to ensure there is no additonal unwanted
     *            launch). Will usually be zero.
     */
    public void waitForResults(int nbResult, int timeoutMs, int waitAdditionalMs)
    {
        Calendar start = Calendar.getInstance();
        while (JqmClientFactory.getClient().newQuery().invoke().size() < nbResult
                && Calendar.getInstance().getTimeInMillis() - start.getTimeInMillis() <= timeoutMs)
        {
            sleepms(100);
        }
        if (JqmClientFactory.getClient().newQuery().invoke().size() < nbResult)
        {
            throw new RuntimeException("expected result count was not reached in specified timeout");
        }
        sleepms(waitAdditionalMs);
    }

    /**
     * Wait for a given amount of ended job instances (OK or KO). Shortcut for {@link #waitForResults(int, int, int)} with 0ms of additional
     * wait time.
     *
     * @param nbResult
     *            the expected result count
     * @param timeoutMs
     *            give up after this (throws a RuntimeException)
     */
    public void waitForResults(int nbResult, int timeoutMs)
    {
        waitForResults(nbResult, timeoutMs, 0);
    }

    private void sleepms(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            // not an issue in tests
        }
    }

    /**
     * Stops all engines. Only returns when engines are fully stopped.
     */
    public void stop()
    {
        if (!hasStarted)
        {
            throw new IllegalStateException("cannot stop a tester which has not started");
        }
        for (JqmEngineOperations op : this.engines.values())
        {
            op.stop();
        }
        JqmClientFactory.resetClient();
        cnx.close();
        s.stop();
        waitDbStop();
        s = null;
        hasStarted = false;
        this.engines.clear();
    }

    private void waitDbStop()
    {
        while (s.getState() != 16)
        {
            this.sleepms(1);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // CLEANUP
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Removes all job instances from the queues and the history.
     *
     * @param em
     */
    public void cleanupOperationalDbData()
    {
        cnx.runUpdate("deliverable_delete_all");
        cnx.runUpdate("message_delete_all");
        cnx.runUpdate("history_delete_all");
        cnx.runUpdate("jiprm_delete_all");
        cnx.runUpdate("ji_delete_all");
        cnx.commit();
    }

    /**
     * Deletes all job definitions. This calls {@link #cleanupOperationalDbData()}
     *
     * @param em
     */
    public void cleanupAllJobDefinitions()
    {
        cleanupOperationalDbData();

        cnx.runUpdate("jdprm_delete_all");
        cnx.runUpdate("jd_delete_all");
        cnx.commit();
    }

    ///////////////////////////////////////////////////////////////////////////
    // TEST RESULT ANALYSIS HELPERS
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Helper query (directly uses {@link JdbcQuery}). Gives the count of all ended (KO and OK) job instances.
     */
    public int getHistoryAllCount()
    {
        return JqmClientFactory.getClient().newQuery().invoke().size();
    }

    /**
     * Helper query (directly uses {@link JdbcQuery}). Gives the count of all non-ended (waiting in queue, running...) job instances.
     */
    public int getQueueAllCount()
    {
        return JqmClientFactory.getClient().newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).invoke().size();
    }

    /**
     * Helper query (directly uses {@link JdbcQuery}). Gives the count of all OK-ended job instances.
     */
    public int getOkCount()
    {
        return JqmClientFactory.getClient().newQuery().addStatusFilter(State.ENDED).invoke().size();
    }

    /**
     * Helper query (directly uses {@link JdbcQuery}). Gives the count of all non-OK-ended job instances.
     */
    public int getNonOkCount()
    {
        return JqmClientFactory.getClient().newQuery().addStatusFilter(State.CRASHED).addStatusFilter(State.KILLED).invoke().size();
    }

    /**
     * Helper method. Tests if {@link #getOkCount()} is equal to the given parameter.
     */
    public boolean testOkCount(long expectedOkCount)
    {
        return getOkCount() == expectedOkCount;
    }

    /**
     * Helper method. Tests if {@link #getNonOkCount()} is equal to the given parameter.
     */
    public boolean testKoCount(long expectedKoCount)
    {
        return getNonOkCount() == expectedKoCount;
    }

    /**
     * Helper method. Tests if {@link #getOkCount()} is equal to the first parameter and if {@link #getNonOkCount()} is equal to the second
     * parameter.
     */
    public boolean testCounts(long expectedOkCount, long expectedKoCount)
    {
        return testOkCount(expectedOkCount) && testKoCount(expectedKoCount);
    }

    /**
     * Version of {@link JqmClient#getDeliverableContent(Deliverable)} which does not require the web service APIs to be enabled to work.
     * Also, returned files do not self-destruct on stream close.<br>
     * See the javadoc of the original method for details.
     *
     * @throws FileNotFoundException
     */
    public InputStream getDeliverableContent(Deliverable file) throws FileNotFoundException
    {
        List<com.enioka.jqm.model.Deliverable> dd = com.enioka.jqm.model.Deliverable.select(cnx, "deliverable_select_by_id", file.getId());
        if (dd.isEmpty())
        {
            throw new JqmInvalidRequestException("no deliverable with this ID");
        }

        com.enioka.jqm.model.Deliverable d = dd.get(0);
        JobInstance ji = JqmClientFactory.getClient().newQuery().setJobInstanceId(d.getJobId()).invoke().get(0);
        String nodeName = ji.getNodeName();
        Node n = nodes.get(nodeName);

        return new FileInputStream(FilenameUtils.concat(n.getDlRepo(), file.getFilePath()));
    }
}
