/**
 * Copyright © 2013 enioka. All rights reserved
 * Authors: Marc-Antoine GOUILLART (marc-antoine.gouillart@enioka.com)
 *          Pierre COPPEE (pierre.coppee@enioka.com)
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

package com.enioka.jqm.tools;

import java.io.Closeable;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.SocketException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLNonTransientException;
import java.sql.SQLTransientException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipFile;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.naming.spi.NamingManager;

import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha512Hash;
import org.apache.shiro.util.ByteSource;
import org.apache.shiro.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.jdbc.NonUniqueResultException;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JndiObjectResource;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.RUser;

// TODO: cnx should be first arg.

/**
 * This is a helper class for internal use only.
 *
 */
final class Helpers
{
    private static Logger jqmlogger = LoggerFactory.getLogger(Helpers.class);

    // The one and only Database context in the engine.
    private static Db _db;

    // Resource file contains at least the jqm jdbc connection definition. Static because JNDI root context is common to the whole JVM.
    static String resourceFile = "resources.xml";

    private Helpers()
    {

    }

    /**
     * Get a fresh connection on the engine database.
     *
     * @return a DbConn.
     */
    static DbConn getNewDbSession()
    {
        getDb();
        return _db.getConn();
    }

    static void setDb(Db db)
    {
        _db = db;
    }

    static Db getDb()
    {
        if (_db == null)
        {
            _db = createFactory();
        }
        return _db;
    }

    static boolean isDbInitialized()
    {
        return _db != null;
    }

    private static Db createFactory()
    {
        try
        {
            // Load optional properties file
            Properties p = Db.loadProperties();

            // Connect to DB.
            Db n = new Db(p);
            p.put("com.enioka.jqm.jdbc.contextobject", n); // Share the DataSource in engine and client.
            JqmClientFactory.setProperties(p);

            return n;
        }
        catch (Exception e)
        {
            jqmlogger.error("Unable to connect with the database. Maybe your configuration file is wrong. "
                    + "Please check the password or the url in the $JQM_DIR/conf/resources.xml", e);
            throw new JqmInitError("Database connection issue", e);
        }
    }

    static void closeQuietly(Closeable zf)
    {
        if (zf != null)
        {
            try
            {
                zf.close();
            }
            catch (Exception e)
            {
                jqmlogger.warn("could not close closeable item", e);
            }
        }
    }

    static void registerJndiIfNeeded()
    {
        try
        {
            JndiContext.createJndiContext();
        }
        catch (NamingException e)
        {
            throw new JqmInitError("Could not register the JNDI provider", e);
        }
    }

    /**
     * For internal test use only <br/>
     * <bold>WARNING</bold> This will invalidate all open DB sessions!
     */
    static void resetDb()
    {
        _db = null;
    }

    /**
     * Create a text message that will be stored in the database. Must be called inside a transaction.
     */
    static void createMessage(String textMessage, JobInstance jobInstance, DbConn cnx)
    {
        cnx.runUpdate("message_insert", jobInstance.getId(), textMessage);
    }

    /**
     * Create a Deliverable inside the database that will track a file created by a JobInstance Must be called from inside a transaction
     *
     * @param path
     *                             FilePath (relative to a root directory - cf. Node)
     * @param originalFileName
     *                             FileName
     * @param fileFamily
     *                             File family (may be null). E.g.: "daily report"
     * @param jobId
     *                             Job Instance ID
     * @param cnx
     *                             the DbConn to use.
     */
    static int createDeliverable(String path, String originalFileName, String fileFamily, Integer jobId, DbConn cnx)
    {
        QueryResult qr = cnx.runUpdate("deliverable_insert", fileFamily, path, jobId, originalFileName, UUID.randomUUID().toString());
        return qr.getGeneratedId();
    }

    /**
     * Checks if a parameter exists. If it exists, it is left untouched. If it doesn't, it is created. Only works for parameters which key
     * is unique. Must be called from within an open transaction.
     */
    static void initSingleParam(String key, String initValue, DbConn cnx)
    {
        try
        {
            cnx.runSelectSingle("globalprm_select_by_key", 2, String.class, key);
            return;
        }
        catch (NoResultException e)
        {
            GlobalParameter.create(cnx, key, initValue);
        }
        catch (NonUniqueResultException e)
        {
            // It exists! Nothing to do...
        }
    }

    /**
     * Checks if a parameter exists. If it exists, it is updated. If it doesn't, it is created. Only works for parameters which key is
     * unique. Will create a transaction on the given entity manager.
     */
    static void setSingleParam(String key, String value, DbConn cnx)
    {
        QueryResult r = cnx.runUpdate("globalprm_update_value_by_key", value, key);
        if (r.nbUpdated == 0)
        {
            cnx.runUpdate("globalprm_insert", key, value);
        }
        cnx.commit();
    }

    static void checkConfiguration(String nodeName, DbConn cnx)
    {
        // Node
        List<Node> nodes = Node.select(cnx, "node_select_by_key", nodeName);
        if (nodes.size() == 0)
        {
            throw new JqmInitError("The node does not exist. It must be referenced (CLI option createnode) before it can be used");
        }
        Node nn = nodes.get(0);

        if (!StringUtils.hasText(nn.getDlRepo()) || !StringUtils.hasText(nn.getRepo()) || !StringUtils.hasText(nn.getTmpDirectory()))
        {
            throw new JqmInitError(
                    "The node does not have all its paths specified. Check node configuration (or recreate it with the CLI).");
        }

        // Default queue
        List<Queue> defaultQueues = Queue.select(cnx, "q_select_default");
        if (defaultQueues.size() == 0)
        {
            throw new JqmInitError("There is no default queue. Correct this (for example with CLI option -u, or with the web admin)");
        }
        if (defaultQueues.size() > 1)
        {
            throw new JqmInitError(
                    "There is more than one default queue. Correct this (for example with CLI option -u, or with the web admin)");
        }

        // Deployment parameters
        int i = cnx.runSelectSingle("dp_select_count_for_node", Integer.class, nn.getId());
        if (i == 0)
        {
            jqmlogger.warn(
                    "This node is not bound to any queue. Either use the GUI to bind it or use CLI option -u to bind it to the default queue");
        }

        // Roles
        List<RRole> roles = RRole.select(cnx, "role_select_by_key", "administrator");
        if (roles.size() != 1)
        {
            throw new JqmInitError("The 'administrator' role does not exist. It is needed for the APIs. Run CLI option -u to create it.");
        }

        // Mail session
        i = cnx.runSelectSingle("jndi_select_count_for_key", Integer.class, "mail/default");
        if (i == 0L)
        {
            throw new JqmInitError("Mail session named mail/default does not exist but is required for the engine to run"
                    + ". Use CLI option -u to create an empty one or use the admin web GUI to create it.");
        }
    }

    /**
     * Creates or updates a node.<br>
     * This method makes the assumption metadata is valid. e.g. there MUST be a single default queue.<br>
     * Call {@link #updateConfiguration(EntityManager)} before to be sure if necessary.
     *
     * @param nodeName
     *                     name of the node that should be created or updated (if incompletely defined only)
     * @param em
     *                     an EntityManager on which a transaction will be opened.
     */
    static void updateNodeConfiguration(String nodeName, DbConn cnx, int port)
    {
        // Node
        Integer nodeId = null;
        try
        {
            nodeId = cnx.runSelectSingle("node_select_by_key", Integer.class, nodeName);
        }
        catch (NoResultException e)
        {
            jqmlogger.info("Node " + nodeName + " does not exist in the configuration and will be created with default values");

            nodeId = Node.create(cnx, nodeName, port, System.getProperty("user.dir") + "/jobs/", System.getProperty("user.dir") + "/jobs/",
                    System.getProperty("user.dir") + "/tmp/", "localhost", "INFO").getId();
            cnx.commit();
        }

        // Deployment parameters
        long i = cnx.runSelectSingle("dp_select_count_for_node", Integer.class, nodeId);
        if (i == 0L)
        {
            jqmlogger.info("As this node is not bound to any queue, it will be set to poll from the default queue with default parameters");
            Integer default_queue_id = cnx.runSelectSingle("q_select_default", 1, Integer.class);
            DeploymentParameter.create(cnx, nodeId, 5, 1000, default_queue_id);

            cnx.commit();
        }
    }

    /**
     * Creates or updates metadata common to all nodes: default queue, global parameters, roles...<br>
     * It is idempotent. It also has the effect of making broken metadata viable again.
     */
    static void updateConfiguration(DbConn cnx)
    {
        // Default queue
        Queue q = null;
        long i = cnx.runSelectSingle("q_select_count_all", Integer.class);
        if (i == 0L)
        {
            Queue.create(cnx, "DEFAULT", "default queue", true);
            jqmlogger.info("A default queue was created in the configuration");
        }
        else
        {
            try
            {
                jqmlogger.info("Default queue is named " + cnx.runSelectSingle("q_select_default", 4, String.class));
            }
            catch (NonUniqueResultException e)
            {
                // Faulty configuration, but why not
                q = Queue.select(cnx, "q_select_all").get(0);
                cnx.runUpdate("q_update_default_none");
                cnx.runUpdate("q_update_default_by_id", q.getId());
                jqmlogger.info("Queue " + q.getName() + " was modified to become the default queue as there were multiple default queues");
            }
            catch (NoResultException e)
            {
                // Faulty configuration, but why not
                q = Queue.select(cnx, "q_select_all").get(0);
                cnx.runUpdate("q_update_default_none");
                cnx.runUpdate("q_update_default_by_id", q.getId());
                jqmlogger.info("Queue " + q.getName() + " was modified to become the default queue as there were multiple default queues");
            }
        }

        // Global parameters
        initSingleParam("mavenRepo", "http://repo1.maven.org/maven2/", cnx);
        initSingleParam(Constants.GP_DEFAULT_CONNECTION_KEY, Constants.GP_JQM_CONNECTION_ALIAS, cnx);
        initSingleParam("logFilePerLaunch", "true", cnx);
        initSingleParam("internalPollingPeriodMs", "60000", cnx);
        initSingleParam("disableWsApi", "false", cnx);
        initSingleParam("enableWsApiSsl", "false", cnx);
        initSingleParam("enableWsApiAuth", "true", cnx);
        initSingleParam("enableInternalPki", "true", cnx);

        // Roles
        RRole adminr = createRoleIfMissing(cnx, "administrator", "all permissions without exception", "*:*");
        createRoleIfMissing(cnx, "config admin", "can read and write all configuration, except security configuration", "node:*", "queue:*",
                "qmapping:*", "jndi:*", "prm:*", "jd:*");
        createRoleIfMissing(cnx, "config viewer", "can read all configuration except for security configuration", "node:read", "queue:read",
                "qmapping:read", "jndi:read", "prm:read", "jd:read");
        createRoleIfMissing(cnx, "client", "can use the full client API except reading logs, files and altering position", "node:read",
                "queue:read", "job_instance:*", "jd:read");
        createRoleIfMissing(cnx, "client power user", "can use the full client API", "node:read", "queue:read", "job_instance:*", "jd:read",
                "logs:read", "queue_position:create", "files:read");
        createRoleIfMissing(cnx, "client read only", "can query job instances and get their files", "queue:read", "job_instance:read",
                "logs:read", "files:read");

        // Users
        createUserIfMissing(cnx, "root", new SecureRandomNumberGenerator().nextBytes().toHex(), "all powerful user", adminr.getName());

        // Mail session
        i = cnx.runSelectSingle("jndi_select_count_for_key", Integer.class, "mail/default");
        if (i == 0)
        {
            Map<String, String> prms = new HashMap<>();
            prms.put("smtpServerHost", "smtp.gmail.com");

            JndiObjectResource.create(cnx, "mail/default", "javax.mail.Session", "com.enioka.jqm.providers.MailSessionFactory",
                    "default parameters used to send e-mails", true, prms);
        }

        // Done
        cnx.commit();
    }

    static RRole createRoleIfMissing(DbConn cnx, String roleName, String description, String... permissions)
    {
        List<RRole> rr = RRole.select(cnx, "role_select_by_key", roleName);
        if (rr.size() == 0)
        {
            RRole.create(cnx, roleName, description, permissions);
            return RRole.select(cnx, "role_select_by_key", roleName).get(0);
        }
        return rr.get(0);
    }

    /**
     * Creates a new user if does not exist. If it exists, it is unlocked and roles are reset (password is untouched).
     *
     * @param cnx
     * @param login
     * @param password
     *                        the raw password. it will be hashed.
     * @param description
     * @param roles
     */
    static void createUserIfMissing(DbConn cnx, String login, String password, String description, String... roles)
    {
        try
        {
            int userId = cnx.runSelectSingle("user_select_id_by_key", Integer.class, login);
            cnx.runUpdate("user_update_enable_by_id", userId);
            RUser.set_roles(cnx, userId, roles);
        }
        catch (NoResultException e)
        {
            String saltS = null;
            String hash = null;
            if (null != password && !"".equals(password))
            {
                ByteSource salt = new SecureRandomNumberGenerator().nextBytes();
                hash = new Sha512Hash(password, salt, 100000).toHex();
                saltS = salt.toHex();
            }

            RUser.create(cnx, login, hash, saltS, roles);
        }
    }

    static String getMavenVersion()
    {
        String res = System.getProperty("mavenVersion");
        if (res != null)
        {
            return res;
        }

        InputStream is = Helpers.class.getResourceAsStream("/META-INF/maven/com.enioka.jqm/jqm-engine/pom.properties");
        Properties p = new Properties();
        try
        {
            p.load(is);
            res = p.getProperty("version");
        }
        catch (Exception e)
        {
            res = "maven version not found";
            jqmlogger.warn("maven version not found");
        }
        return res;
    }

    static JobDef findJobDef(String applicationName, DbConn cnx)
    {
        List<JobDef> jj = JobDef.select(cnx, "jd_select_by_key", applicationName);
        if (jj.size() == 0)
        {
            return null;
        }
        return jj.get(0);
    }

    static void dumpParameters(DbConn cnx, Node n)
    {
        String terse = GlobalParameter.getParameter(cnx, "disableVerboseStartup", "false");
        if ("false".equals(terse))
        {
            jqmlogger.info("Global cluster parameters are as follow:");
            List<GlobalParameter> prms = GlobalParameter.selectAll(cnx);
            for (GlobalParameter prm : prms)
            {
                jqmlogger.info(String.format("\t%1$s = %2$s", prm.getKey(), prm.getValue()));
            }

            jqmlogger.info("Node parameters are as follow:");
            jqmlogger.info("\tfile produced storage directory: " + n.getDlRepo());
            jqmlogger.info("\tHTTP listening interface: " + n.getDns());
            jqmlogger.info("\tlooks for payloads inside: " + n.getRepo());
            jqmlogger.info("\tlog level: " + n.getRootLogLevel());
            jqmlogger.info("\ttemp files will be created inside: " + n.getTmpDirectory());
            jqmlogger.info("\tJMX registry port: " + n.getJmxRegistryPort());
            jqmlogger.info("\tJMX server port: " + n.getJmxServerPort());
            jqmlogger.info("\tHTTP listening port: " + n.getPort());
            jqmlogger.info("\tAPI admin enabled: " + n.getLoadApiAdmin());
            jqmlogger.info("\tAPI client enabled: " + n.getLoadApiClient());
            jqmlogger.info("\tAPI simple enabled: " + n.getLoapApiSimple());

            // Pollers
            jqmlogger.info("Node polling parameters are as follow:");
            List<DeploymentParameter> dps = DeploymentParameter.select(cnx, "dp_select_for_node", n.getId());
            for (DeploymentParameter dp : dps)
            {
                String q = cnx.runSelectSingle("q_select_by_id", String.class, dp.getQueue()); // TODO: avoid this query with a join.
                jqmlogger.info(
                        "\t" + q + " - every " + dp.getPollingInterval() + "ms - maximum " + dp.getNbThread() + " concurrent threads");
            }

            // Some technical data from the JVM hosting the node
            Runtime rt = Runtime.getRuntime();
            jqmlogger.info("JVM parameters are as follow:");
            jqmlogger.info("\tMax usable memory reported by Java runtime, MB: " + (int) (rt.maxMemory() / 1024 / 1024));
            jqmlogger.info("\tJVM arguments are: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        }
    }

    /**
     * Send a mail message using a JNDI resource.<br>
     * As JNDI resource providers are inside the EXT class loader, this uses reflection. This method is basically a bonus on top of the
     * MailSessionFactory offered to payloads, making it accessible also to the engine.
     *
     * @param to
     * @param subject
     * @param body
     * @param mailSessionJndiAlias
     * @throws MessagingException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void sendMessage(String to, String subject, String body, String mailSessionJndiAlias)
    {
        jqmlogger.debug("sending mail to " + to + " - subject is " + subject);
        ClassLoader extLoader = getExtClassLoader();
        extLoader = extLoader == null ? Helpers.class.getClassLoader() : extLoader;
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Object mailSession = null;

        try
        {
            mailSession = InitialContext.doLookup(mailSessionJndiAlias);
        }
        catch (NamingException e)
        {
            throw new JqmRuntimeException("could not find mail session description", e);
        }

        try
        {
            Thread.currentThread().setContextClassLoader(extLoader);
            Class transportZ = extLoader.loadClass("javax.mail.Transport");
            Class sessionZ = extLoader.loadClass("javax.mail.Session");
            Class mimeMessageZ = extLoader.loadClass("javax.mail.internet.MimeMessage");
            Class messageZ = extLoader.loadClass("javax.mail.Message");
            Class recipientTypeZ = extLoader.loadClass("javax.mail.Message$RecipientType");
            Object msg = mimeMessageZ.getConstructor(sessionZ).newInstance(mailSession);

            mimeMessageZ.getMethod("setRecipients", recipientTypeZ, String.class).invoke(msg, recipientTypeZ.getField("TO").get(null), to);
            mimeMessageZ.getMethod("setSubject", String.class).invoke(msg, subject);
            mimeMessageZ.getMethod("setText", String.class).invoke(msg, body);

            transportZ.getMethod("send", messageZ).invoke(null, msg);
            jqmlogger.trace("Mail was sent");
        }
        catch (Exception e)
        {
            throw new JqmRuntimeException("an exception occurred during mail sending", e);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    static void sendEndMessage(JobInstance ji)
    {
        try
        {
            String message = "The Job number " + ji.getId() + " finished correctly\n\n" + "Job description:\n" + "\n" + "- Parent: "
                    + ji.getParentId() + "\n" + "- User name: " + ji.getUserName() + "\n" + "- Session ID: " + ji.getSessionID() + "\n"
                    + "\n" + "Best regards,\n";
            sendMessage(ji.getEmail(), "[JQM] Job: " + ji.getId() + " ENDED", message, "mail/default");
        }
        catch (Exception e)
        {
            jqmlogger.warn("Could not send email. Job has nevertheless run correctly", e);
        }
    }

    static ClassLoader getExtClassLoader()
    {
        try
        {
            return ((JndiContext) NamingManager.getInitialContext(null)).getExtCl();
        }
        catch (NamingException e)
        {
            // Don't do anything - this actually cannot happen. Death to checked exceptions.
            return null;
        }
    }

    static boolean testDbFailure(Exception e)
    {
        return (e instanceof SQLTransientException) || (e.getCause() instanceof SQLTransientException)
                || (e.getCause() != null && e.getCause().getCause() instanceof SQLTransientException)
                || (e.getCause() != null && e.getCause().getCause() != null
                        && e.getCause().getCause().getCause() instanceof SQLTransientException)
                || (e.getCause() != null && e.getCause().getCause() != null && e.getCause().getCause().getCause() != null
                        && e.getCause().getCause().getCause().getCause() instanceof SQLTransientException)
                || (e.getCause() != null && e.getCause() instanceof SQLException
                        && e.getMessage().equals("Failed to validate a newly established connection."))
                || (e.getCause() != null && e.getCause().getCause() != null && e.getCause().getCause() instanceof SocketException)
                || (e.getCause() != null && e.getCause().getMessage().equals("This connection has been closed"))
                || (e.getCause() != null && e.getCause() instanceof SQLNonTransientConnectionException)
                || (e.getCause() != null && e.getCause() instanceof SQLNonTransientException
                        && e.getCause().getMessage().equals("connection exception: closed"));
    }
}
