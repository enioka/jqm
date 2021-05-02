package com.enioka.jqm.engine;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.UUID;

import javax.naming.NamingException;
import javax.naming.spi.NamingManager;
import javax.sql.DataSource;

import com.enioka.jqm.api.JobManager;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.api.JqmClientException;
import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.runner.api.JqmKillException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Message;
import com.enioka.jqm.model.State;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobInstanceEngineApi implements JobManager
{
    private static Logger jqmlogger = LoggerFactory.getLogger(JobInstanceEngineApi.class);

    private JobInstance ji;
    private Calendar lastPeek = null;

    JobInstanceEngineApi(JobInstance ji)
    {
        this.ji = ji;
    }

    ///////////////////////////////////////////////////////////////////////////
    // API methods
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Create a {@link com.enioka.jqm.model.Message} with the given message. The {@link com.enioka.jqm.model.History} to link to is deduced
     * from the context.
     *
     * @param msg
     * @throws JqmKillException
     */
    @Override
    public void sendMsg(String msg)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            Message.create(cnx, msg, ji.getId());
            cnx.commit();
        }
    }

    /**
     * Update the {@link com.enioka.jqm.model.History} with the given progress data.
     *
     * @param msg
     * @throws JqmKillException
     */
    @Override
    public void sendProgress(Integer msg)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            this.ji.setProgress(msg); // Not persisted, but useful to the Loader.
            cnx.runUpdate("jj_update_progress_by_id", msg, ji.getId());
            cnx.commit();
        }
    }

    @Override
    public Integer enqueue(String applicationName, String user, String mail, String sessionId, String application, String module,
            String keyword1, String keyword2, String keyword3, Map<String, String> parameters)
    {
        JobRequest jr = getJqmClient().newJobRequest(applicationName, user);
        jr.setApplicationName(applicationName);
        jr.setUser(user == null ? ji.getUserName() : user);
        jr.setEmail(mail);
        jr.setSessionID(sessionId == null ? this.ji.getSessionID() : sessionId);
        jr.setApplication(application == null ? this.ji.getJD().getApplication() : application);
        jr.setModule(module == null ? this.ji.getJD().getModule() : module);
        jr.setKeyword1(keyword1);
        jr.setKeyword2(keyword2);
        jr.setKeyword3(keyword3);
        jr.setParentID(this.ji.getId());
        if (parameters != null)
        {
            jr.setParameters(parameters);
        }

        return jr.enqueue();
    }

    @Override
    public Integer enqueueSync(String applicationName, String user, String mail, String sessionId, String application, String module,
            String keyword1, String keyword2, String keyword3, Map<String, String> parameters)
    {
        int i = enqueue(applicationName, user, mail, sessionId, application, module, keyword1, keyword2, keyword3, parameters);
        waitChild(i);
        return i;
    }

    @Override
    public void waitChild(int id)
    {
        JqmClient c = getJqmClient();
        Query q = c.newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).setJobInstanceId(id);

        while (!q.invoke().isEmpty())
        {
            try
            {
                Thread.sleep(1000);
                handleInstructions();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void waitChildren()
    {
        JqmClient c = getJqmClient();
        Query q = c.newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).setParentId(ji.getId());

        while (!q.invoke().isEmpty())
        {
            try
            {
                Thread.sleep(1000);
                handleInstructions();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public Integer addDeliverable(String path, String fileLabel)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            String outputRoot = this.ji.getNode().getDlRepo();
            String ext = FilenameUtils.getExtension(path);
            String relDestPath = ji.getJD().getApplicationName() + "/" + ji.getId() + "/" + UUID.randomUUID() + "." + ext;
            String absDestPath = FilenameUtils.concat(outputRoot, relDestPath);
            String fileName = FilenameUtils.getName(path);

            jqmlogger.debug("A deliverable is added. Stored as " + absDestPath + ". Initial name: " + fileName);
            FileUtils.moveFile(new File(path), new File(absDestPath));
            cnx.commit();
            int res = Helpers.createDeliverable(relDestPath, fileName, fileLabel, this.ji.getId(), cnx);
            cnx.commit();
            return res;
        }
        catch (IOException e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public File getWorkDir()
    {
        File f = new File(FilenameUtils.concat(ji.getNode().getTmpDirectory(), Integer.toString(this.ji.getId())));
        if (!f.isDirectory())
        {
            try
            {
                FileUtils.forceMkdir(f);
            }
            catch (Exception e)
            {
                throw new JqmRuntimeException("Could not create work directory", e);
            }
        }
        return f;
    }

    @Override
    public String defaultConnect()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            return GlobalParameter.getParameter(cnx, "defaultConnection", null);
        }
    }

    @Override
    public DataSource getDefaultConnection() throws NamingException
    {
        Object dso = NamingManager.getInitialContext(null).lookup(defaultConnect());
        return (DataSource) dso;
    }

    @Override
    public boolean hasEnded(int jobId)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            cnx.runSelectSingle("ji_select_instruction_by_id", String.class, jobId);
            return false;
        }
        catch (NoResultException e)
        {
            return true;
        }
    }

    @Override
    public Boolean hasSucceeded(int requestId)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            State s = State.valueOf(cnx.runSelectSingle("history_select_state_by_id", String.class, requestId));
            return s.equals(State.ENDED);
        }
        catch (NoResultException e)
        {
            return null;
        }
    }

    @Override
    public Boolean hasFailed(int requestId)
    {
        Boolean b = hasSucceeded(requestId);
        if (b == null)
        {
            return b;
        }
        else
        {
            return !b;
        }
    }

    @Override
    public void yield()
    {
        handleInstructions();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////////

    private JqmClient getJqmClient()
    {
        return JqmClientFactory.getClient();
    }

    private void handleInstructions()
    {
        // Throttle: only peek once every 1 second.
        if (lastPeek != null && Calendar.getInstance().getTimeInMillis() - lastPeek.getTimeInMillis() < 1000L)
        {
            return;
        }

        try (DbConn cnx = Helpers.getNewDbSession())
        {
            Instruction s = Instruction.valueOf(cnx.runSelectSingle("ji_select_instruction_by_id", String.class, ji.getId()));
            jqmlogger.trace("Analysis: should JI " + ji.getId() + " get killed or paused? Current instruction is " + s);
            if (s.equals(Instruction.KILL))
            {
                jqmlogger.info("Job will be killed at the request of a user");
                Helpers.closeQuietly(cnx); // Close at once. Some DB drivers (Oracle...) will use the interruption state
                                           // and reset.
                Thread.currentThread().interrupt();
                throw new JqmKillException("This job" + "(ID: " + ji.getId() + ")" + " has been forcefully ended by a user");
            }

            if (s.equals(Instruction.PAUSE))
            {
                jqmlogger.info("Job will be paused at the request of a user");
                sendMsg("Pause is beginning");

                while (s.equals(Instruction.PAUSE))
                {
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (InterruptedException e)
                    {
                        throw new RuntimeException("job thread was interrupted");
                    }
                    s = Instruction.valueOf(cnx.runSelectSingle("ji_select_instruction_by_id", String.class, ji.getId()));
                }
                jqmlogger.info("Job instance is resuming");
                sendMsg("Job instance is resuming");
            }

            // TEMP: #319 workaround. Full implementation should be in the engine, not here.
            int priority = cnx.runSelectSingle("ji_select_priority_by_id", Integer.class, ji.getId());
            if (priority != 0)
            {
                if (Thread.currentThread().getPriority() != priority)
                {
                    Thread.currentThread().setPriority(priority);
                }
            }
        }
        finally
        {
            lastPeek = Calendar.getInstance();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Simple accessors
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public Integer jobApplicationId()
    {
        return this.ji.getJdId();
    }

    @Override
    public Integer parentID()
    {
        return this.ji.getParentId();
    }

    @Override
    public Integer jobInstanceID()
    {
        return this.ji.getId();
    }

    @Override
    public Boolean canBeRestarted()
    {
        return this.ji.getJD().isCanBeRestarted();
    }

    @Override
    public String applicationName()
    {
        return this.ji.getJD().getApplicationName();
    }

    @Override
    public String sessionID()
    {
        return this.ji.getSessionID();
    }

    @Override
    public String application()
    {
        return this.ji.getJD().getApplication();
    }

    @Override
    public String module()
    {
        return this.ji.getJD().getModule();
    }

    @Override
    public String keyword1()
    {
        return this.ji.getKeyword1();
    }

    @Override
    public String keyword2()
    {
        return this.ji.getKeyword2();
    }

    @Override
    public String keyword3()
    {
        return this.ji.getKeyword3();
    }

    @Override
    public String definitionKeyword1()
    {
        return this.ji.getJD().getKeyword1();
    }

    @Override
    public String definitionKeyword2()
    {
        return this.ji.getJD().getKeyword2();
    }

    @Override
    public String definitionKeyword3()
    {
        return this.ji.getJD().getKeyword3();
    }

    @Override
    public String userName()
    {
        return this.ji.getUserName();
    }

    @Override
    public Map<String, String> parameters()
    {
        return this.ji.getPrms();
    }
}
