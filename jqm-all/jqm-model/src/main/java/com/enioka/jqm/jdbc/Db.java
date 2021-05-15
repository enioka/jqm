package com.enioka.jqm.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.enioka.jqm.loader.Loader;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for all database-related operations, from initialization to schema upgrade, as well as creating sessions for querying the
 * database.
 */
public class Db
{
    private static Logger jqmlogger = LoggerFactory.getLogger(Db.class);

    /**
     * The version of the schema as it described in the current Maven artifact
     */
    private static final int SCHEMA_VERSION = 2;

    /**
     * The SCHEMA_VERSION version is backward compatible until this version
     */
    private static final int SCHEMA_COMPATIBLE_VERSION = 2;

    private DataSource _ds = null;
    private DbAdapter adapter = null;
    private String product;
    private Properties p = null;

    /**
     * Connects to the database by retrieving a DataDource from JNDI (with every parameter set to default, including the JNDI alias for the
     * DataSource being jdbc/jqm).
     */
    public Db()
    {
        this(null);
    }

    /**
     * Constructor for cases when a DataSource is readily available (and not retrieved through JNDI).
     *
     * @param ds
     *            the existing DataSource.
     * @param updateSchema
     *            set to true if the database schema should upgrade (if needed) during initialization
     */
    public Db(DataSource ds, boolean updateSchema)
    {
        this._ds = ds;
        this.p = new Properties();
        this.p.setProperty("com.enioka.jqm.jdbc.allowSchemaUpdate", updateSchema + "");
        initAdapterAndSchema(this.p);
    }

    /**
     * Main constructor. Properties may be null. Properties are not documented on purpose, as this is a private JQM API.
     *
     * @param props
     */
    @SuppressWarnings("unchecked")
    public Db(Properties properties)
    {
        this.p = properties != null ? properties : new Properties();

        if (p.containsKey("com.enioka.jqm.jdbc.url"))
        {
            // In this case - full JDBC construction, not from JNDI. Only works for HSQLDB, useful only in tests.

            // Allow upgrade by default in this case (this is used only in tests)
            String url = p.getProperty("com.enioka.jqm.jdbc.url");

            DataSource ds = null;
            if (url.contains("jdbc:hsqldb"))
            {
                Class<? extends DataSource> dsclass;
                try
                {
                    dsclass = (Class<? extends DataSource>) Class.forName("org.hsqldb.jdbc.JDBCDataSource");
                }
                catch (ClassNotFoundException e)
                {
                    throw new IllegalStateException("The driver for database HSQLDB was not found in the classpath");
                }

                try
                {
                    ds = dsclass.getConstructor().newInstance();
                    dsclass.getMethod("setDatabase", String.class).invoke(ds, "jdbc:hsqldb:mem:testdbengine");
                }
                catch (Exception e)
                {
                    throw new DatabaseException("could not create datasource. See errors below.", e);
                }
            }
            else
            {
                throw new IllegalArgumentException("this constructor does not support this database type - URL " + url);
            }

            this._ds = ds;
            initAdapterAndSchema(p);
        }
        else
        {
            // Standard case: fetch a DataSource from our XML file, then JNDI.
            // (in that order: JNDI depends on the database in out implementation... could be embarassing to do otherwise)
            String dsName = p.getProperty("com.enioka.jqm.jdbc.datasource", "jdbc/jqm");
            int retryCount = Integer.parseInt(p.getProperty("com.enioka.jqm.jdbc.initialRetries", "10"));
            int waitMs = Integer.parseInt(p.getProperty("com.enioka.jqm.jdbc.initialRetryWaitMs", "10000"));

            // Ascending compatibility with v1.x: old name for the DataSource.
            String oldName = p.getProperty("javax.persistence.nonJtaDataSource");
            if (oldName != null)
            {
                dsName = oldName;
            }

            // Try to open the pool.
            this._ds = getDataSource(dsName, retryCount, waitMs);

            // This is a hack. Some containers will use root context as default for JNDI (WebSphere, Glassfish...), other will use
            // java:/comp/env/ (Tomcat...). So if we actually know the required alias, we try both, and the user only has to provide a
            // root JNDI alias that will work in both cases.
            if (this._ds == null)
            {
                jqmlogger.warn("JNDI alias {} was not found. Trying with java:/comp/env/ prefix", dsName);
                dsName = "java:/comp/env/" + dsName;
                this._ds = getDataSource(dsName, retryCount, waitMs);
            }

            if (this._ds == null)
            {
                throw new DatabaseException("no data source found");
            }

            initAdapterAndSchema(p);
        }
    }

    private DataSource getDataSource(String dsName, int retryCount, int waitMs)
    {
        int retries = 0;
        DataSource res = null;

        // Fom XML file
        try
        {
            res = BootstrapDatasourceLoader.getDatasourceFromXml(dsName);
        }
        catch (NamingException e)
        {
            return null;
        }

        while (res == null)
        {
            try
            {
                res = (DataSource) InitialContext.doLookup(dsName);
            }
            catch (NameNotFoundException e2)
            {
                res = null;
            }
            catch (Exception e)
            {
                if (++retries > retryCount)
                {
                    break;
                }
                // TODO: naming exception name does not exist.

                String msg = e.getLocalizedMessage();
                if (e.getCause() != null)
                {
                    msg += " - " + e.getCause().getLocalizedMessage();
                    jqmlogger.error(
                            "Database not available: " + msg + ". Retry " + retries + "/" + retryCount + ". Waiting for database...");

                    try
                    {
                        Thread.sleep(waitMs);
                    }
                    catch (InterruptedException e1)
                    {
                        // Nothing to do.
                    }
                }
            }
        }
        return res;
    }

    /**
     * Main database initialization. To be called only when _ds is a valid DataSource.
     */
    private void initAdapterAndSchema(Properties p)
    {
        // Select a DB adapter
        initAdapter(p);

        // Adapt and cache all JQM SQL queries with the selected DB adapter.
        initQueries();

        // Create or upgrade schema
        boolean upgrade = Boolean.parseBoolean(p.getProperty("com.enioka.jqm.jdbc.allowSchemaUpdate", "false"));
        if (upgrade)
        {
            dbUpgrade();
        }

        // First contact with the DB is version checking (if no connection opened by pool).
        // If DB is in wrong version or not available, just wait for it to be ready.
        boolean versionValid = false;
        while (!versionValid)
        {
            try
            {
                checkSchemaVersion();
                versionValid = true;
            }
            catch (Exception e)
            {
                String msg = e.getLocalizedMessage();
                if (e.getCause() != null)
                {
                    msg += " - " + e.getCause().getLocalizedMessage();
                }
                jqmlogger.error("Database not ready: " + msg + ". Waiting for database...");
                try
                {
                    Thread.sleep(10000);
                }
                catch (Exception e2)
                {
                }
            }
        }
    }

    private void checkSchemaVersion()
    {
        int db_schema_version = 0;
        int db_schema_compat_version = 0;
        Map<String, Object> rs = null;
        try (DbConn cnx = this.getConn())
        {
            rs = cnx.runSelectSingleRow("version_select_latest");
            db_schema_version = (Integer) rs.get("VERSION_D1");

            db_schema_compat_version = (Integer) rs.get("COMPAT_D1");
        }
        catch (NoResultException e)
        {
            // Database is to be created, so version 0.
        }
        catch (Exception z)
        {
            throw new DatabaseException("could not retrieve version information from database", z);
        }

        if (SCHEMA_VERSION > db_schema_version)
        {
            if (SCHEMA_COMPATIBLE_VERSION <= db_schema_version)
            {
                // OK
                return;
            }
        }
        if (SCHEMA_VERSION == db_schema_version)
        {
            // OK
            return;
        }
        if (SCHEMA_VERSION < db_schema_version)
        {
            if (SCHEMA_VERSION >= db_schema_compat_version)
            {
                // OK
                return;
            }
        }

        // If here, not OK at all.
        throw new DatabaseException("Database schema version mismatch. This library can work with schema versions from "
                + SCHEMA_COMPATIBLE_VERSION + " to at least " + SCHEMA_VERSION + " but database is in version " + db_schema_version);
    }

    /**
     * Updates the database. Never call this during normal operations, upgrade is a user-controlled operation.
     */
    private void dbUpgrade()
    {
        DbConn cnx = this.getConn();
        Map<String, Object> rs = null;
        int db_schema_version = 0;
        try
        {
            rs = cnx.runSelectSingleRow("version_select_latest");
            db_schema_version = (Integer) rs.get("VERSION_D1");
        }
        catch (Exception e)
        {
            // Database is to be created, so version 0 is OK.
        }
        cnx.rollback();

        if (SCHEMA_VERSION > db_schema_version)
        {
            jqmlogger.warn("Database is being upgraded from version {} to version {}", db_schema_version, SCHEMA_VERSION);

            // Upgrade scripts are named from_to.sql with 5 padding (e.g. 00000_00003.sql)
            // We try to find the fastest path (e.g. a direct 00000_00005.sql for creating a version 5 schema from nothing)
            // This is a simplistic and non-optimal algorithm as we try only a single path (no going back)

            int loop_from = db_schema_version;
            int to = db_schema_version;
            List<String> toApply = new ArrayList<>();
            toApply.addAll(adapter.preSchemaCreationScripts());

            while (to != SCHEMA_VERSION)
            {
                boolean progressed = false;
                for (int loop_to = SCHEMA_VERSION; loop_to > db_schema_version; loop_to--)
                {
                    String migrationFileName = String.format("/sql/%05d_%05d.sql", loop_from, loop_to);
                    jqmlogger.debug("Trying migration script {}", migrationFileName);
                    if (Db.class.getResource(migrationFileName) != null)
                    {
                        toApply.add(migrationFileName);
                        to = loop_to;
                        loop_from = loop_to;
                        progressed = true;
                        break;
                    }
                }

                if (!progressed)
                {
                    break;
                }
            }
            if (to != SCHEMA_VERSION)
            {
                throw new DatabaseException(
                        "There is no migration path from version " + db_schema_version + " to version " + SCHEMA_VERSION);
            }

            for (String s : toApply)
            {
                jqmlogger.info("Running migration script {}", s);
                ScriptRunner.run(cnx, s);
            }
            cnx.commit(); // Yes, really. For advanced DB!

            cnx.close(); // HSQLDB does not refresh its schema without this.
            cnx = getConn();

            cnx.runUpdate("version_insert", SCHEMA_VERSION, SCHEMA_COMPATIBLE_VERSION);
            cnx.commit();
            jqmlogger.info("Database is now up to date");
        }
        else
        {
            jqmlogger.info("Database is already up to date");
        }
        cnx.close();
    }

    /**
     * Creates the adapter for the target database. The list of available adapters comes either from the OSGi service registry or from a
     * property.
     */
    private void initAdapter(Properties p)
    {
        DbAdapter newAdpt = null;

        String dbAdaptersProp = p.getProperty("com.enioka.jqm.jdbc.adapters");

        try (Connection tmp = _ds.getConnection())
        {
            DatabaseMetaData meta = tmp.getMetaData();
            product = meta.getDatabaseProductName().toLowerCase();

            if (dbAdaptersProp == null || dbAdaptersProp.isEmpty())
            {
                // OSGi case - always fails outside an OSGi framework with class not found exceptions.
                jqmlogger.info("Database adapter search: using OSGi services");
                try
                {
                    BundleContext context = FrameworkUtil.getBundle(getClass()).getBundleContext();
                    Loader<DbAdapter> loader = new Loader<DbAdapter>(context, DbAdapter.class, "(Adapter-Type=*)");
                    loader.start();

                    for (ServiceReference<?> ref : loader.references)
                    {
                        jqmlogger.info("\t\tFound DB adapter plugin {}:{}", ref.getBundle().getSymbolicName(),
                                ref.getBundle().getVersion());
                        DbAdapter newAdapter = (DbAdapter) context.getService(ref);
                        if (newAdapter.compatibleWith(meta))
                        {
                            adapter = newAdapter;
                            break;
                        }
                    }
                }
                catch (Exception e)
                {
                    throw new DatabaseException("Issue when loading database adapter", e);
                }
            }
            else
            {
                // Standard Java class loading environment.
                jqmlogger.info("Database adapter search: using initialization property {}", dbAdaptersProp);
                for (String s : dbAdaptersProp.split(","))
                {
                    try
                    {
                        // Note this cannot work in an OSGi classloader - it could not see classes outside the current bundle. without
                        // manifest imports.
                        Class<? extends DbAdapter> clazz = Db.class.getClassLoader().loadClass(s).asSubclass(DbAdapter.class);
                        newAdpt = clazz.getConstructor().newInstance();
                        if (newAdpt.compatibleWith(meta))
                        {
                            adapter = newAdpt;
                            break;
                        }
                    }
                    catch (Exception e)
                    {
                        throw new DatabaseException("Issue when loading database adapter named: " + s, e);
                    }
                }
            }
        }
        catch (SQLException e1)
        {
            throw new DatabaseException("Cannot connect to the database", e1);
        }

        if (adapter == null)
        {
            throw new DatabaseException("Unsupported database! There is no JQM database adapter compatible with product name " + product);
        }
        else
        {
            jqmlogger.info("Using database adapter {}", adapter.getClass().getCanonicalName());
        }
    }

    /**
     * Create the query cache (with db-specific queries)
     */
    private void initQueries()
    {
        DbConn cnx = getConn();
        adapter.prepare(p, cnx._cnx);
        cnx.close();
    }

    /**
     * A connection to the database. Should be short-lived. No transaction active by default.
     *
     * @return a new open connection.
     */
    public DbConn getConn()
    {
        Connection cnx = null;
        try
        {
            Thread.interrupted(); // this is VERY sad. Needed for Oracle driver which otherwise fails spectacularly.
            cnx = _ds.getConnection();
            if (cnx.getAutoCommit())
            {
                cnx.setAutoCommit(false);
                cnx.rollback(); // To ensure no open transaction created by the pool before changing TX mode
            }

            if (cnx.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED)
            {
                cnx.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            }

            return new DbConn(this, cnx);
        }
        catch (SQLException e)
        {
            DbHelper.closeQuietly(cnx); // May have been left open when the pool has given us a failed connection.
            throw new DatabaseException(e);
        }
    }

    /**
     * Gets the interpolated text of a query from cache. If key does not exist, an exception is thrown.
     *
     * @param key
     *            name of the query
     * @return the query text
     */
    String getQuery(String key)
    {
        String res = this.adapter.getSqlText(key);
        if (res == null)
        {
            throw new DatabaseException("Query " + key + " does not exist");
        }
        return res;
    }

    DbAdapter getAdapter()
    {
        return this.adapter;
    }

    public String getProduct()
    {
        return this.product;
    }
}
