package com.enioka.jqm.tools;

import java.io.File;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Properties;

import javax.naming.NamingException;
import javax.naming.spi.NamingManager;

import com.enioka.jqm.api.JobInstanceTracker;
import com.enioka.jqm.api.JobRunner;
import com.enioka.jqm.api.JobRunnerCallback;
import com.enioka.jqm.api.JobRunnerException;
import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.api.SimpleApiSecurity;
import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DatabaseUnreachableException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.History;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.State;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The role of this class is to track the actual run of a JI. An instance is created when the engine decides a JI should run. It delegates
 * the running stuff to a {@link com.enioka.jqm.api.JobRunner}s it selects and concentrates itself on plumbing common to all job instances.
 */
class RunningJobInstance implements Runnable, JobRunnerCallback
{
    private Logger jqmlogger = LoggerFactory.getLogger(RunningJobInstance.class);

    private JobInstance ji;
    private JqmEngine engine = null;
    private QueuePoller qp = null;
    private RunningJobInstanceManager manager = null;

    private JobInstanceTracker tracker;
    private State resultStatus = State.ATTRIBUTED;
    private Boolean isDone = false;
    private Calendar endDate = null;
    private JobRunner jr = null;

    /**
     * Constructor for JI coming from queue pollers.
     *
     * @param ji
     * @param qp
     */
    RunningJobInstance(JobInstance ji, QueuePoller qp)
    {
        this.ji = ji;
        this.qp = qp;
        this.engine = qp.getEngine();
        this.manager = engine.getRunningJobInstanceManager();
    }

    /**
     * Constructor for single runner
     */
    RunningJobInstance(JobInstance ji, JobRunner jr)
    {
        this.ji = ji;
        this.jr = jr;
    }

    ///////////////////////////////////////////////////////////////////////////
    // START RUN
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void run()
    {
        try
        {
            actualRun();
        }
        catch (Throwable t)
        {
            jqmlogger.error("An unexpected error has occurred - the engine may have become unstable", t);
        }
    }

    private void actualRun()
    {
        // Set thread name
        Thread.currentThread().setName(this.ji.getJD().getApplicationName() + ";payload;" + this.ji.getId());

        // As a precaution (real date is taken from database, unless in case of dire crash and for JMX).
        ji.setExecutionDate(Calendar.getInstance());

        // Engine Callbacks (e.g. used by multi log handling)
        if (this.engine != null && this.engine.getHandler() != null)
        {
            this.engine.getHandler().onJobInstancePreparing(this.ji);
        }

        // Priority?
        if (this.ji.getPriority() != null && this.ji.getPriority() >= Thread.MIN_PRIORITY && this.ji.getPriority() <= Thread.MAX_PRIORITY)
        {
            Thread.currentThread().setPriority(this.ji.getPriority());
        }

        // Select runner
        if (jr == null)
        {
            jr = this.engine.getRunnerManager().getRunner(this.ji);
        }

        // Log
        jqmlogger.debug("A loader/runner thread has just started for Job Instance " + this.ji.getId() + ". Jar is: "
                + this.ji.getJD().getJarPath() + " - class is: " + this.ji.getJD().getJavaClassName() + ". Runner used is "
                + jr.getClass().getCanonicalName() + ".");

        // Disabled?
        if (!this.ji.getJD().isEnabled())
        {
            jqmlogger.info("Job Instance " + this.ji.getId() + " will actually not truly run as its Job Definition is disabled");
            this.ji.setProgress(-1); // Not persisted here, but useful to endOfRunDb
            resultStatus = State.ENDED;
            endOfRun();
            return;
        }

        // Create tracker
        tracker = jr.getTracker(this.ji, new JobInstanceEngineApi(this.ji), this);

        // Block needing the database
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            // Cache heating & co, loader-specific.
            tracker.initialize(cnx);

            // Update of the job status, dates & co
            QueryResult qr = cnx.runUpdate("jj_update_run_by_id", this.ji.getId());
            if (qr.nbUpdated == 0)
            {
                // This means the JI has been killed or has disappeared.
                jqmlogger.warn("Trying to run a job which disappeared or is not in ATTRIBUTED state (likely killed) " + this.ji.getId());
                if (this.manager != null)
                {
                    manager.signalEndOfRun(this);
                }
                return;
            }
            cnx.commit();
        }
        catch (JobRunnerException e)
        {
            jqmlogger.warn("Runner could not prepare for job " + this.ji.getJD().getApplicationName(), e);
            resultStatus = State.CRASHED;
            endOfRun();
            return;
        }
        catch (DatabaseUnreachableException e)
        {
            jqmlogger.error("connection to database lost - loader " + this.ji.getId() + " will be restarted later");
            jqmlogger.trace("connection error was:", e);
            this.engine.loaderRestartNeeded(this);
            if (this.engine.getHandler() != null)
            {
                this.engine.getHandler().onJobInstanceDone(this.ji);
            }
            return;
        }
        catch (RuntimeException e)
        {
            jqmlogger.error("a database related operation has failed and cannot be recovered", e);
            resultStatus = State.CRASHED;
            endOfRun();
            return;
        }

        // Actual launch
        try
        {
            resultStatus = tracker.run();
        }
        catch (Exception e)
        {
            jqmlogger.info("Job instance " + this.ji.getId() + " has crashed. Exception was:", e);
            resultStatus = State.CRASHED;
        }

        // Job instance has now ended its run
        try
        {
            endOfRun();
        }
        catch (Exception e)
        {
            jqmlogger.error("An error occurred while finalizing the job instance.", e);
        }

        jqmlogger.debug("End of loader for JobInstance " + this.ji.getId() + ". Thread will now end");
    }

    ///////////////////////////////////////////////////////////////////////////
    // Deal with end of run - persist
    ///////////////////////////////////////////////////////////////////////////

    /**
     * For external payloads. This is used to force the end of run.
     */
    void endOfRun(State s)
    {
        this.resultStatus = s;
        endOfRun();
    }

    private void endOfRun()
    {
        // Register end date as soon as possible to be as exact as possible (sending mails may take time for example)
        endDate = GregorianCalendar.getInstance(Locale.getDefault());

        // This block is needed for external payloads, as the single runner may forcefully call endOfRun.
        synchronized (this)
        {
            if (!isDone)
            {
                isDone = true;
            }
            else
            {
                return;
            }
        }

        // Release the slot so as to allow other job instances to run (first op!)
        if (this.manager != null)
        {
            this.manager.signalEndOfRun(this);
        }

        // Send e-mail before releasing the slot - it may be long
        if (ji.getEmail() != null)
        {
            try
            {
                Helpers.sendEndMessage(ji);
            }
            catch (Exception e)
            {
                jqmlogger.warn("An e-mail could not be sent. No impact on the engine.", e);
            }
        }

        // Clean temp dir (if it exists)
        File tmpDir = new File(FilenameUtils.concat(this.ji.getNode().getTmpDirectory(), "" + this.ji.getId()));
        if (tmpDir.isDirectory())
        {
            try
            {
                if (FileUtils.deleteQuietly(tmpDir))
                {
                    jqmlogger.trace("temp directory was removed");
                }
                else
                {
                    jqmlogger.warn("Could not remove temp directory " + tmpDir.getAbsolutePath()
                            + "for this job instance. There may be open handlers remaining open.");
                }
            }
            catch (Exception e)
            {
                jqmlogger.warn("Could not remove temp directory for unusual reasons", e);
            }
        }

        // Runner-specific stuff
        if (this.tracker != null) // happens when disabled
        {
            this.tracker.wrap();
        }

        // Unregister logger at the very end only
        if (this.engine != null && this.engine.getHandler() != null)
        {
            this.engine.getHandler().onJobInstanceDone(ji);
        }

        // Part needing DB connection with specific failure handling code.
        endOfRunDb();
    }

    /**
     * Part of the endOfRun process that needs the database. May be deferred if the database is not available.
     */
    void endOfRunDb()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            // Done: put inside history & remove instance from queue.
            History.create(cnx, this.ji, this.resultStatus, endDate);
            jqmlogger.trace("An History was just created for job instance " + this.ji.getId());
            cnx.runUpdate("ji_delete_by_id", this.ji.getId());
            cnx.commit();
        }
        catch (DatabaseUnreachableException e)
        {
            jqmlogger.error("connection to database lost - loader " + this.ji.getId() + " will need delayed finalization");
            jqmlogger.trace("connection error was:", e.getCause());
            this.engine.loaderFinalizationNeeded(this);
        }
        catch (RuntimeException e)
        {
            jqmlogger.error("a database related operation has failed and cannot be recovered", e);
            throw e;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // CB interface
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isJmxEnabled()
    {
        return this.engine != null ? this.engine.loadJmxBeans : false;
    }

    @Override
    public String getJmxBeanName()
    {
        return "com.enioka.jqm:type=Node.Queue.JobInstance,Node=" + this.engine.getNode().getName() + ",Queue="
                + this.qp.getQueue().getName() + ",name=" + this.ji.getId();
    }

    @Override
    public void killThroughClientApi()
    {
        Properties props = new Properties();
        props.put("com.enioka.jqm.jdbc.contextobject", Helpers.getDb());
        JqmClientFactory.getClient("uncached", props, false).killJob(this.ji.getId());
    }

    void handleInstruction(Instruction instruction)
    {
        if (this.tracker != null)
        {
            try
            {
                this.tracker.handleInstruction(instruction);
            }
            catch (Exception e)
            {
                jqmlogger.error("Could not handle instruction inside job instance runner.", e);
            }
        }
    }

    @Override
    public Long getRunTimeSeconds()
    {
        if (this.ji.getExecutionDate() == null)
        {
            DbConn cnx = Helpers.getNewDbSession();
            this.ji.setExecutionDate(cnx.runSelectSingle("ji_select_execution_date_by_id", Calendar.class, this.ji.getId()));
            cnx.close();
        }
        if (this.ji.getExecutionDate() == null)
        {
            return 0L;
        }
        return (Calendar.getInstance().getTimeInMillis() - this.ji.getExecutionDate().getTimeInMillis()) / 1000;
    }

    @Override
    public ClassLoader getExtensionClassloader()
    {
        ClassLoader extLoader = null;
        try
        {
            extLoader = ((JndiContext) NamingManager.getInitialContext(null)).getExtCl();
        }
        catch (NamingException e)
        {
            jqmlogger.warn("could not find ext directory class loader. Il will not be used", e);
        }
        return extLoader;
    }

    @Override
    public ClassLoader getEngineClassloader()
    {
        return this.getClass().getClassLoader();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helpers
    ///////////////////////////////////////////////////////////////////////////

    int getId()
    {
        return this.ji.getId();
    }

    boolean isDone()
    {
        return this.isDone;
    }

    @Override
    public Entry<String, String> getWebApiUser(DbConn cnx)
    {
        SimpleApiSecurity.Duet d = SimpleApiSecurity.getId(cnx);
        return new AbstractMap.SimpleEntry<String, String>(d.usr, d.pass);
    }

    @Override
    public String getWebApiLocalUrl(DbConn cnx)
    {
        // Do not use port from engine.getNode, as it may have been set AFTER engine startup.
        Node node = Node.select_single(cnx, "node_select_by_id", this.engine.getNode().getId());
        boolean useSsl = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "enableWsApiSsl", "false"));
        return (useSsl ? "https://localhost:" : "http://localhost:") + node.getPort();
    }
}
