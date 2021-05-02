package com.enioka.jqm.test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.hsqldb.Server;
import org.hsqldb.jdbc.JDBCDataSource;

import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.engine.JqmSingleRunner;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.RuntimeParameter;
import com.enioka.jqm.model.State;
import com.enioka.jqm.model.JobDef.PathType;

/**
 * This class allows to start a stripped-down version of the JQM engine and run a payload synchronously inside it.<br>
 * It is the only supported way to unit test a payload (be it with JUnit or a simple main() method). <br>
 * <br>
 * Limitations:
 * <ul>
 * <li>The job will actually run inside the current class loader (in the full engine, each job instance has its own class loader)</li>
 * <li>This for testing one job only. Only one job instance will run! If the test itself enqueues new launch request, they will be ignored.
 * For testing interactions between job instances, integration tests on an embedded JQM engine are required.</li>
 * <li>If using resources (JNDI), they must be put inside a resource.xml file at the root of classloader search.</li>
 * <li>Resource providers and corresponding drivers must be put inside testing class path (for example by putting them inside pom.xml with a
 * <code>test</code> scope)</li>
 * <li>To ease tests, the launch is synchronous. Obviously, real life instances are asynchronous. To test asynchronous launches, use an
 * embedded engine (integration test) with the much more complicated {@link JqmAsyncTester}.</li>
 * <li>If files are created by the payload, they are stored inside a temporary directory that is not removed at the end of the run.</li>
 * </ul>
 * <br>
 * For example, a simple JUnit test could be:
 *
 * <pre>
 * {@code public void testOne()
 * {
 *     JobInstance res = JqmTester.create("com.enioka.jqm.test.Payload1").run();
 *     Assert.assertEquals(State.ENDED, res.getState());
 * }
 * </pre>
 */
public class JqmTester
{
    private static Server s;

    private Db db = null;
    private DbConn cnx = null;

    private Node node = null;
    private Integer jd = null;
    private Integer q = null;
    private Integer ji = null;
    private File resDirectoryPath;

    private JqmTester(String className)
    {
        s = Common.createHsqlServer();
        s.start();

        JDBCDataSource ds = new JDBCDataSource();
        ds.setDatabase("jdbc:hsqldb:mem:" + s.getDatabaseName(0, true));
        db = new Db(ds, true);
        cnx = db.getConn();

        JqmSingleRunner.setConnection(db);

        Properties p2 = new Properties();
        p2.put("com.enioka.jqm.jdbc.contextobject", db);
        JqmClientFactory.setProperties(p2);

        // Needed parameters
        GlobalParameter.setParameter(cnx, "defaultConnection", "");
        cnx.commit();

        // Ext dir
        File extDir = new File("./ext");
        if (!extDir.exists() && !extDir.mkdir())
        {
            throw new RuntimeException(new IOException("./ext directory does not exist and cannot create it"));
        }

        // Create node
        resDirectoryPath = Common.createTempDirectory();
        node = Node.create(cnx, "testtempnode", 12, resDirectoryPath.getAbsolutePath(), resDirectoryPath.getAbsolutePath(),
                resDirectoryPath.getAbsolutePath(), "test", "INFO");

        q = Queue.create(cnx, "default", "default test queue", true); // Only useful because JobDef.queue is non-null

        jd = JobDef.create(cnx, "test application", className, null, "/dev/null", q, 0, "TestApplication", null, null, null, null, null,
                false, null, PathType.MEMORY);

        ji = JobInstance.enqueue(cnx, State.SUBMITTED, q, jd, null, null, null, null, null, null, null, null, null, false, false, null, 0,
                Instruction.RUN, null);
        cnx.runUpdate("ji_update_status_by_id", node.getId(), ji);

        cnx.commit();
    }

    public static JqmTester create(String className)
    {
        return new JqmTester(className);
    }

    public JqmTester addParameter(String key, String value)
    {
        RuntimeParameter.create(cnx, ji, key, value);
        cnx.commit();
        return this;
    }

    private void close()
    {
        s.stop();
        s.shutdown();
        cnx.close();
        // JqmClientFactory.resetClient();
        JqmClientFactory.setProperties(new Properties());
    }

    public com.enioka.jqm.client.api.JobInstance run()
    {
        com.enioka.jqm.client.api.JobInstance res = JqmSingleRunner.run(ji);

        close();
        return res;
    }
}
