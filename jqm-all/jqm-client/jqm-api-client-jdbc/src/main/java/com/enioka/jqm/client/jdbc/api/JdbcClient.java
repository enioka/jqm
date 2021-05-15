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

package com.enioka.jqm.client.jdbc.api;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.net.ssl.SSLContext;

import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.api.JqmClientException;
import com.enioka.jqm.client.api.JqmException;
import com.enioka.jqm.client.api.JqmInvalidRequestException;
import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.api.Query.Sort;
import com.enioka.jqm.client.api.Query.SortOrder;
import com.enioka.jqm.client.api.Query.SortSpec;
import com.enioka.jqm.client.api.QueueStatus;
import com.enioka.jqm.client.api.Schedule;
import com.enioka.jqm.client.shared.JobRequestBaseImpl;
import com.enioka.jqm.client.shared.JqmClientEnqueueCallback;
import com.enioka.jqm.client.shared.QueryBaseImpl;
import com.enioka.jqm.client.shared.JqmClientQuerySubmitCallback;
import com.enioka.jqm.client.shared.SelfDestructFileStream;
import com.enioka.jqm.client.shared.SimpleApiSecurity;
import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.Db;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.DbManager;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.jdbc.NonUniqueResultException;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.model.Deliverable;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.History;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.JobDefParameter;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Message;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.RuntimeParameter;
import com.enioka.jqm.model.ScheduledJob;
import com.enioka.jqm.model.State;

import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JQM client API entry point.
 */
final class JdbcClient implements JqmClient, JqmClientEnqueueCallback, JqmClientQuerySubmitCallback
{
    private static Logger jqmlogger = LoggerFactory.getLogger(JdbcClient.class);
    private static final int IN_CLAUSE_LIMIT = 500;

    private Db db = null;
    private String protocol = null;
    Properties p;

    // /////////////////////////////////////////////////////////////////////
    // Construction/Connection
    // /////////////////////////////////////////////////////////////////////

    // No public constructor. MUST use factory.
    JdbcClient(Properties p)
    {
        this.p = p;
        if (p.containsKey("com.enioka.jqm.jdbc.contextobject"))
        {
            jqmlogger.trace("database context present in properties");
            db = (Db) p.get("com.enioka.jqm.jdbc.contextobject");
        }
        // otherwise db is created on first use.
    }

    private Db createFactory()
    {
        jqmlogger.debug("Creating connection factory to database");
        return DbManager.getDb(p);
    }

    DbConn getDbSession()
    {
        if (db == null)
        {
            db = createFactory();
        }

        try
        {
            return db.getConn();
        }
        catch (Exception e)
        {
            jqmlogger.error("Could not create database session.", e);
            throw new JqmClientException("Could not create database session", e);
        }
    }

    private void closeQuietly(Closeable closeable)
    {
        try
        {
            if (closeable != null)
            {
                closeable.close();
            }
        }
        catch (IOException ioe)
        {
            // ignore
        }
    }

    @Override
    public void dispose()
    {
        SimpleApiSecurity.dispose();
        this.db = null;
        p = null;
    }

    // /////////////////////////////////////////////////////////////////////
    // Enqueue functions
    // /////////////////////////////////////////////////////////////////////

    @Override
    public JobRequest newJobRequest(String applicationName, String user)
    {
        return new JobRequestBaseImpl(this).setApplicationName(applicationName).setUser(user);
    }

    @Override
    public int enqueue(JobRequestBaseImpl runRequest)
    {
        jqmlogger.trace("BEGINING ENQUEUE - request is for application name " + runRequest.getApplicationName());

        // Form validity.
        if ((runRequest.getApplicationName() == null || runRequest.getApplicationName().trim().isEmpty())
                && runRequest.getScheduleId() == null)
        {
            throw new JqmClientException("Invalid execution request: applicationName is empty");
        }
        runRequest.setParameters(runRequest.getParameters()); // This will validate parameters.

        try (DbConn cnx = getDbSession())
        {
            return enqueueWithCnx(runRequest, cnx);
        }
    }

    private int enqueueWithCnx(JobRequestBaseImpl runRequest, DbConn cnx)
    {
        // New schedule?
        if (runRequest.getRecurrence() != null && !runRequest.getRecurrence().trim().isEmpty())
        {
            int res = createSchedule(runRequest, cnx);
            cnx.commit();
            return res;
        }

        // Run existing schedule?
        ScheduledJob sj = null;
        if (runRequest.getScheduleId() != null)
        {
            List<ScheduledJob> sjj = ScheduledJob.select(cnx, "sj_select_by_id", runRequest.getScheduleId());
            if (sjj.size() == 0)
            {
                jqmlogger.error("Invalid job request: no schedule with ID " + runRequest.getScheduleId());
                throw new JqmInvalidRequestException("Invalid job request: no schedule with ID " + runRequest.getScheduleId());
            }
            if (sjj.size() > 1)
            {
                jqmlogger.error("Inconsistent metadata: multiple schedules with ID " + runRequest.getScheduleId());
                throw new JqmClientException("Inconsistent metadata: multiple schedules with ID " + runRequest.getScheduleId());
            }
            sj = sjj.get(0);
        }

        // First, get the JobDef.
        JobDef jobDef = null;
        if (sj == null)
        {
            // Standard case: execution by applicationName.
            try
            {
                jobDef = JobDef.select_key(cnx, runRequest.getApplicationName());
            }
            catch (NonUniqueResultException ex)
            {
                jqmlogger.error(
                        "There are multiple Job definition named " + runRequest.getApplicationName() + ". Inconsistent configuration.");
                throw new JqmInvalidRequestException("There are multiple Job definition named " + runRequest.getApplicationName());
            }
            catch (NoResultException ex)
            {
                jqmlogger.error("Job definition named " + runRequest.getApplicationName() + " does not exist");
                throw new JqmInvalidRequestException("no job definition named " + runRequest.getApplicationName());
            }
        }
        else
        {
            // Selection by schedule.
            List<JobDef> jdd = JobDef.select(cnx, "jd_select_by_id", sj.getJobDefinition());
            if (jdd.size() == 0)
            {
                jqmlogger.error("Invalid job request: no JobDef with ID " + sj.getJobDefinition());
                throw new JqmInvalidRequestException("Invalid job request: no JobDef with ID " + sj.getJobDefinition());
            }
            if (jdd.size() > 1)
            {
                jqmlogger.error("Inconsistent metadata: multiple JobDef with ID " + sj.getJobDefinition());
                throw new JqmClientException("Inconsistent metadata: multiple JobDef with ID " + sj.getJobDefinition());
            }
            jobDef = jdd.get(0);
        }
        jqmlogger.trace("Job to enqueue is from JobDef " + jobDef.getId());

        // Then check Highlander.
        Integer existing = highlanderMode(jobDef, cnx);
        if (existing != null)
        {
            jqmlogger.trace("JI won't actually be enqueued because a job in highlander mode is currently submitted: " + existing);
            return existing;
        }

        // If here, need to enqueue a new execution request.
        jqmlogger.trace("Not in highlander mode or no currently enqueued instance");

        // Parameters are both from the JobDef and the execution request.
        Map<String, String> prms = JobDefParameter.select_map(cnx, "jdprm_select_all_for_jd", jobDef.getId());
        if (sj != null)
        {
            prms.putAll(sj.getParameters());
        }
        prms.putAll(runRequest.getParameters());

        // On which queue?
        Integer queue_id = null;
        if (runRequest.getQueueName() != null)
        {
            // use requested key if given.
            try
            {
                queue_id = cnx.runSelectSingle("q_select_by_key", 1, Integer.class, runRequest.getQueueName());
            }
            catch (NoResultException e)
            {
                throw new JqmInvalidRequestException("Requested queue " + runRequest.getQueueName() + " does not exist", e);
            }
        }
        else if (sj != null && sj.getQueue() != null)
        {
            queue_id = sj.getQueue();
        }
        else
        {
            // use default queue otherwise
            queue_id = jobDef.getQueue();
        }

        // Priority can come from schedule, JD, request. (in order of ascending priority)
        Integer priority = null;
        if (sj != null)
        {
            priority = sj.getPriority();
        }
        if (jobDef.getPriority() != null)
        {
            priority = jobDef.getPriority();
        }
        if (runRequest.getPriority() != null)
        {
            priority = runRequest.getPriority();
        }

        // Decide what the starting state should be.
        State startingState = State.SUBMITTED; // The default.
        if (runRequest.getRunAfter() != null)
        {
            startingState = State.SCHEDULED;
        }
        else if (runRequest.getStartState() != null)
        {
            startingState = State.valueOf(runRequest.getStartState().toString());
        }

        // Now create the JI
        try
        {
            int id = JobInstance.enqueue(cnx, startingState, queue_id, jobDef.getId(), runRequest.getApplication(),
                    runRequest.getParentID(), runRequest.getModule(), runRequest.getKeyword1(), runRequest.getKeyword2(),
                    runRequest.getKeyword3(), runRequest.getSessionID(), runRequest.getUser(), runRequest.getEmail(), jobDef.isHighlander(),
                    sj != null || runRequest.getRunAfter() != null, runRequest.getRunAfter(), priority, Instruction.RUN, prms);

            jqmlogger.trace("JI just created: " + id);
            cnx.commit();
            return id;
        }
        catch (NoResultException e)
        {
            throw new JqmInvalidRequestException("An entity specified in the execution request does not exist", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not create new JobInstance", e);
        }
    }

    @Override
    public int enqueue(String applicationName, String userName)
    {
        return this.newJobRequest(applicationName, userName).enqueue();
    }

    @Override
    public int enqueueFromHistory(int jobIdToCopy)
    {
        try (DbConn cnx = getDbSession())
        {
            return enqueue(getJobRequest(jobIdToCopy, cnx));
        }
        catch (NoResultException e)
        {
            throw new JqmInvalidRequestException("No job for this ID in the history");
        }
    }

    // Helper. Current transaction is committed in some cases.
    private Integer highlanderMode(JobDef jd, DbConn cnx)
    {
        if (!jd.isHighlander())
        {
            return null;
        }

        try
        {
            Integer existing = cnx.runSelectSingle("ji_select_existing_highlander", Integer.class, jd.getId());
            return existing;
        }
        catch (NoResultException ex)
        {
            // Just continue, this means no existing waiting JI in queue.
        }

        // Now we need to actually synchronize through the database to avoid double posting
        // TODO: use a dedicated table, not the JobDef one. Will avoid locking the configuration.
        ResultSet rs = cnx.runSelect(true, "jd_select_by_id", jd.getId());

        // Now we have a lock, just retry - some other client may have created a job instance recently.
        try
        {
            Integer existing = cnx.runSelectSingle("ji_select_existing_highlander", Integer.class, jd.getId());
            rs.close();
            cnx.commit(); // Do not keep the lock!
            return existing;
        }
        catch (NoResultException ex)
        {
            // Just continue, this means no existing waiting JI in queue. We keep the lock!
        }
        catch (SQLException e)
        {
            // Who cares.
            jqmlogger.warn("Issue when closing a ResultSet. Transaction or session leak is possible.", e);
        }

        jqmlogger.trace("Highlander mode analysis is done: nor existing JO, must create a new one. Lock is hold.");
        return null;
    }

    /**
     * Internal helper to create a new execution request from an History row.<br>
     * To be called for a single row only, not for converting multiple History elements.<br>
     * Does not create a transaction, and no need for an active transaction.
     *
     * @param launchId
     *            the ID of the launch (was the ID of the JI, now the ID of the History object)
     * @param cnx
     *            an open DB session
     * @return a new execution request
     */
    private JobRequestBaseImpl getJobRequest(int launchId, DbConn cnx)
    {
        Map<String, String> prms = RuntimeParameter.select_map(cnx, "jiprm_select_by_ji", launchId);
        ResultSet rs = cnx.runSelect("history_select_reenqueue_by_id", launchId);

        try
        {
            if (!rs.next())
            {
                throw new JqmInvalidRequestException("There is no past laucnh iwth ID " + launchId);
            }
        }
        catch (SQLException e1)
        {
            throw new JqmClientException("Internal JQM API error", e1);
        }

        JobRequestBaseImpl jd = new JobRequestBaseImpl(this);
        try
        {
            jd.setApplication(rs.getString(1));
            jd.setApplicationName(rs.getString(2));
            jd.setEmail(rs.getString(3));
            jd.setKeyword1(rs.getString(4));
            jd.setKeyword2(rs.getString(5));
            jd.setKeyword3(rs.getString(6));
            jd.setModule(rs.getString(7));
            jd.setParentID(rs.getInt(8));
            jd.setSessionID(rs.getString(9));
            jd.setUser(rs.getString(10));

            for (Map.Entry<String, String> p : prms.entrySet())
            {
                jd.addParameter(p.getKey(), p.getValue());
            }
        }
        catch (SQLException e)
        {
            throw new JqmClientException("Could not extract History data for launch " + launchId, e);
        }

        return jd;
    }

    private int createSchedule(JobRequestBaseImpl jr, DbConn cnx)
    {
        // The job def
        JobDef jobDef = null;
        try
        {
            jobDef = JobDef.select_key(cnx, jr.getApplicationName());
        }
        catch (NonUniqueResultException ex)
        {
            jqmlogger.error("There are multiple Job definition named " + jr.getApplicationName() + ". Inconsistent configuration.");
            closeQuietly(cnx);
            throw new JqmInvalidRequestException("There are multiple Job definition named " + jr.getApplicationName());
        }
        catch (NoResultException ex)
        {
            jqmlogger.error("Job definition named " + jr.getApplicationName() + " does not exist");
            closeQuietly(cnx);
            throw new JqmInvalidRequestException("no job definition named " + jr.getApplicationName());
        }

        // The queue
        Integer queueId = null; // No override = use JD queue.
        if (jr.getQueueName() != null)
        {
            // use requested key if given.
            queueId = cnx.runSelectSingle("q_select_by_key", 1, Integer.class, jr.getQueueName());
        }

        // The new schedule
        return ScheduledJob.create(cnx, jr.getRecurrence(), jobDef.getId(), queueId, jr.getPriority(), jr.getParameters());
    }

    // /////////////////////////////////////////////////////////////////////
    // Job destruction
    // /////////////////////////////////////////////////////////////////////

    @Override
    public void cancelJob(int idJob)
    {
        jqmlogger.trace("Job instance number " + idJob + " will be cancelled");
        try (DbConn cnx = getDbSession())
        {
            QueryResult res = cnx.runUpdate("jj_update_cancel_by_id", idJob);
            if (res.nbUpdated != 1)
            {
                throw new JqmClientException("the job is already running, has already finished or never existed to begin with");
            }

            History.create(cnx, idJob, State.CANCELLED, null);
            JobInstance.delete_id(cnx, idJob);
            cnx.commit();
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not historise the job instance after it was cancelled", e);
        }
    }

    @Override
    public void deleteJob(int idJob)
    {
        jqmlogger.trace("Job instance number " + idJob + " will be deleted");

        try (DbConn cnx = getDbSession())
        {
            // Two transactions against deadlock.
            QueryResult res = cnx.runUpdate("jj_update_cancel_by_id", idJob);
            cnx.commit();
            if (res.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException(
                        "An attempt was made to delete a job instance that either did not exist or was already running");
            }

            cnx.runUpdate("jiprm_delete_by_ji", idJob);
            cnx.runUpdate("message_delete_by_ji", idJob);
            JobInstance.delete_id(cnx, idJob);
            cnx.commit();
        }
        catch (JqmClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not delete a job (internal error)", e);
        }
    }

    @Override
    public void killJob(int idJob)
    {
        jqmlogger.trace("Job instance number " + idJob + " will be killed (if possible)o");

        // First try to cancel the JI (works if it is not already running)
        try
        {
            cancelJob(idJob);
            return;
        }
        catch (JqmClientException e)
        {
            // Nothing to do - this is thrown if already running. Just go on, this is a standard kill.
        }

        try (DbConn cnx = getDbSession())
        {
            QueryResult res = cnx.runUpdate("jj_update_kill_by_id", idJob);
            if (res.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException("Job instance does not exist or has already finished");
            }

            Message.create(cnx, "Kill attempt on the job", idJob);
            cnx.commit();
        }
        catch (JqmClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not kill a job (internal error)", e);
        }
    }

    @Override
    public void removeRecurrence(int scheduleId)
    {
        try (DbConn cnx = getDbSession())
        {
            cnx.runUpdate("sjprm_delete_all_for_sj", scheduleId);
            QueryResult res = cnx.runUpdate("sj_delete_by_id", scheduleId);
            if (res.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException("Schedule does not exist");
            }
            cnx.commit();
        }
        catch (JqmClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not kill a job (internal error)", e);
        }
    }

    // /////////////////////////////////////////////////////////////////////
    // Job Pause/restart
    // /////////////////////////////////////////////////////////////////////

    @Override
    public void pauseQueuedJob(int idJob)
    {
        jqmlogger.trace("Job instance number " + idJob + " status will be set to HOLDED");

        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_pause_by_id", idJob);
            if (qr.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException(
                        "An attempt was made to pause a job instance that did not exist or was already running.");
            }
            cnx.commit();
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not pause a job (internal error)", e);
        }
    }

    @Override
    public void resumeJob(int jobId)
    {
        resumeQueuedJob(jobId);
    }

    @Override
    public void resumeQueuedJob(int idJob)
    {
        jqmlogger.trace("Job status number " + idJob + " will be resumed");

        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_resume_by_id", idJob);
            if (qr.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException("An attempt was made to pause a job instance that did not exist or was not paused.");
            }
            cnx.commit();
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not resume a job (internal error)", e);
        }
    }

    public int restartCrashedJob(int idJob)
    {
        // History and Job ID have the same ID.
        try (DbConn cnx = getDbSession())
        {
            ResultSet rs = cnx.runSelect("history_select_reenqueue_by_id", idJob);

            if (!rs.next())
            {
                throw new JqmClientException("You cannot restart a job that is not done or which was purged from history");
            }

            JobRequestBaseImpl jr = new JobRequestBaseImpl(this);
            jr.setApplication(rs.getString(1));
            jr.setApplicationName(rs.getString(2));
            jr.setEmail(rs.getString(3));
            jr.setKeyword1(rs.getString(4));
            jr.setKeyword2(rs.getString(5));
            jr.setKeyword3(rs.getString(6));
            jr.setModule(rs.getString(7));
            jr.setParentID(rs.getInt(8));
            jr.setSessionID(rs.getString(9));
            jr.setUser(rs.getString(10));

            State s = State.valueOf(rs.getString(11));
            if (!s.equals(State.CRASHED))
            {
                throw new JqmClientException("You cannot restart a job that has not crashed");
            }

            int res = enqueue(jr);
            cnx.runUpdate("history_delete_by_id", idJob);
            cnx.commit();
            return res;
        }
        catch (JqmClientException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not restart a job (internal error)", e);
        }
    }

    @Override
    public void pauseRunningJob(int jobId)
    {
        jqmlogger.trace("Job instance number " + jobId + " will receive a PAUSE instruction");

        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_instruction_pause_by_id", jobId);
            if (qr.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException("An attempt was made to pause a job instance that did not exist or was already done.");
            }
            cnx.commit();
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not pause a job (internal error)", e);
        }
    }

    @Override
    public void resumeRunningJob(int jobId)
    {
        jqmlogger.trace("Job instance number {}, supposed to be paused, will receive a RUN instruction", jobId);

        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_instruction_resume_by_id", jobId);
            if (qr.nbUpdated != 1)
            {
                throw new JqmInvalidRequestException("An attempt was made to resume a job instance that did not exist or was not paused.");
            }
            cnx.commit();
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not resume a job (internal error)", e);
        }
    }

    // /////////////////////////////////////////////////////////////////////
    // Misc.
    // /////////////////////////////////////////////////////////////////////

    @Override
    public void setJobQueue(int idJob, int idQueue)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_queue_by_id", idQueue, idJob);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Job instance does not exist or has already started");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            if (e.getCause() instanceof SQLIntegrityConstraintViolationException)
                throw new JqmClientException("Queue does not exist", e);
            else
                throw new JqmClientException("could not change the queue of a job (internal error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the queue of a job (internal error)", e);
        }
    }

    @Override
    public void setJobQueue(int idJob, com.enioka.jqm.client.api.Queue queue)
    {
        setJobQueue(idJob, queue.getId());
    }

    @Override
    public void setJobQueuePosition(int idJob, int position)
    {
        try (DbConn cnx = getDbSession())
        {
            // Step 1 : get the queue of this job.
            ResultSet rs1 = cnx.runSelect("ji_select_changequeuepos_by_id", idJob);
            if (!rs1.next())
            {
                throw new JqmInvalidRequestException("Job does not exist or is already running.");
            }
            int queue_id = rs1.getInt(1);
            int internal_id = rs1.getInt(2);
            rs1.close();

            // Step 2 : get the current rank of the JI.
            int current = cnx.runSelectSingle("ji_select_current_pos", Integer.class, internal_id, queue_id);

            // Step 3 : select target position
            int betweenUp = 0;
            int betweenDown = 0;

            if (current == position)
            {
                // Nothing to do
                return;
            }
            else if (current < position)
            {
                betweenDown = position;
                betweenUp = position + 1;
            }
            else
            {
                betweenDown = position - 1;
                betweenUp = position;
            }

            // Step 4 : update the JI.
            List<JobInstance> currentJobs = JobInstance.select(cnx, "ji_select_by_queue", queue_id);
            if (currentJobs.isEmpty())
            {
                cnx.runUpdate("jj_update_rank_by_id", 0, idJob);
            }
            else if (currentJobs.size() < betweenUp)
            {
                cnx.runUpdate("jj_update_rank_by_id", currentJobs.get(currentJobs.size() - 1).getInternalPosition() + 0.00001, idJob);
            }
            else
            {
                // Normal case: put the JI between the two others.
                QueryResult qr = cnx.runUpdate("jj_update_rank_by_id",
                        (currentJobs.get(betweenUp - 1).getInternalPosition() + currentJobs.get(betweenDown - 1).getInternalPosition()) / 2,
                        idJob);
                if (qr.nbUpdated != 1)
                {
                    throw new JqmInvalidRequestException("Job is already running.");
                }
            }
            cnx.commit();
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the queue position of a job (internal error)", e);
        }
    }

    @Override
    public void setJobPriority(int jobId, int priority)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_priority_by_id", priority, jobId);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Job instance does not exist or has already ended");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            throw new JqmClientException("could not change the priority of a job (internal database error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the priority of a job (internal error)", e);
        }
    }

    @Override
    public void setJobRunAfter(int jobId, Calendar whenToRun)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("jj_update_notbefore_by_id", whenToRun, jobId);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Job instance does not exist or has already started");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            throw new JqmClientException("could not change the 'not before time' of a job (internal database error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the 'not before time' of a job (internal error)", e);
        }
    }

    @Override
    public void setScheduleRecurrence(int scheduleId, String cronExpression)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("sj_update_cron_by_id", cronExpression, scheduleId);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Schedule does not exist");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            throw new JqmClientException("could not change the cron expression of a schedule (internal database error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the cron expression of a schedule (internal error)", e);
        }
    }

    @Override
    public void setScheduleQueue(int scheduleId, int queueId)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("sj_update_queue_by_id", queueId, scheduleId);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Schedule does not exist");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            throw new JqmClientException("could not change the queue of a schedule (internal database error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the queue of a schedule (internal error)", e);
        }
    }

    @Override
    public void setSchedulePriority(int scheduleId, int priority)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("sj_update_priority_by_id", priority, scheduleId);

            if (qr.nbUpdated != 1)
            {
                throw new JqmClientException("Schedule does not exist");
            }
            cnx.commit();
        }
        catch (DatabaseException e)
        {
            throw new JqmClientException("could not change the priority of a schedule (internal database error)", e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not change the priority of a schedule (internal error)", e);
        }
    }

    // /////////////////////////////////////////////////////////////////////
    // Job queries
    // /////////////////////////////////////////////////////////////////////

    private String getStringPredicate(String fieldName, String filterValue, List<Object> prms)
    {
        if (filterValue == null)
        {
            return "";
        }
        return getStringPredicate(fieldName, Arrays.asList(filterValue), prms);
    }

    // GetJob helper - String predicates are all created the same way, so this factors some code.
    private String getStringPredicate(String fieldName, List<String> filterValues, List<Object> prms)
    {
        if (filterValues != null && !filterValues.isEmpty())
        {
            String res = "";
            for (String filterValue : filterValues)
            {
                if (filterValue == null)
                {
                    continue;
                }
                if (!filterValue.isEmpty())
                {
                    prms.add(filterValue);
                    if (filterValue.contains("%"))
                    {
                        res += String.format("(%s LIKE ?) OR ", fieldName);
                    }
                    else
                    {
                        res += String.format("(%s = ?) OR ", fieldName);
                    }
                }
                else
                {
                    res += String.format("(%s IS NULL OR %s = '') OR ", fieldName, fieldName);
                }
            }
            if (!res.isEmpty())
            {
                res = "AND (" + res.substring(0, res.length() - 4) + ") ";
                return res;
            }
        }
        return "";
    }

    private String getIntPredicate(String fieldName, Integer filterValue, List<Object> prms)
    {
        if (filterValue != null)
        {
            if (filterValue != -1)
            {
                prms.add(filterValue);
                return String.format("AND %s = ? ", fieldName);
            }
            else
            {
                return String.format("AND %s IS NULL ", fieldName);
            }
        }
        return "";
    }

    private String getCalendarPredicate(String fieldName, Calendar filterValue, String comparison, List<Object> prms)
    {
        if (filterValue != null)
        {
            prms.add(filterValue);
            return String.format("AND (%s %s ?) ", fieldName, comparison);
        }
        else
        {
            return "";
        }
    }

    private String getStatusPredicate(String fieldName, List<com.enioka.jqm.client.api.State> status, List<Object> prms)
    {
        if (status == null || status.isEmpty())
        {
            return "";
        }

        String res = String.format("AND %s IN(UNNEST(?)) ", fieldName);
        prms.add(status);
        return res;
    }

    @Override
    public Query newQuery()
    {
        return new QueryBaseImpl(this);
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getJobs(QueryBaseImpl query)
    {
        if ((query.getFirstRow() != null || query.getPageSize() != null) && query.isQueryLiveInstances() && query.isQueryHistoryInstances())
        {
            // throw new JqmInvalidRequestException("cannot use paging when querying both live and historical instances");
        }
        if (query.isQueryLiveInstances() && query.isQueryHistoryInstances() && query.getSorts().size() > 0)
        {
            // throw new JqmInvalidRequestException("cannot use sorting when querying both live and historical instances");
        }
        if (!query.isQueryHistoryInstances() && !query.isQueryLiveInstances())
        {
            throw new JqmInvalidRequestException(
                    "cannot query nothing - either query live instances, historical instances or both, but not nothing");
        }

        try (DbConn cnx = getDbSession())
        {
            Map<Integer, com.enioka.jqm.client.api.JobInstance> res = new LinkedHashMap<>();

            String wh = "";
            List<Object> prms = new ArrayList<>();

            String q = "", q1 = "", q2 = "";
            String filterCountQuery = "SELECT ";

            // ////////////////////////////////////////
            // Job Instance query
            if (query.isQueryLiveInstances())
            {
                // WHERE
                wh += getIntPredicate("ji.ID", query.getJobInstanceId(), prms);
                wh += getIntPredicate("ji.PARENT", query.getParentId(), prms);
                wh += getStringPredicate("ji.APPLICATION", query.getInstanceApplication(), prms);
                wh += getStringPredicate("ji.MODULE", query.getInstanceModule(), prms);
                wh += getStringPredicate("ji.KEYWORD1", query.getInstanceKeyword1(), prms);
                wh += getStringPredicate("ji.KEYWORD2", query.getInstanceKeyword2(), prms);
                wh += getStringPredicate("ji.KEYWORD3", query.getInstanceKeyword3(), prms);
                wh += getStringPredicate("ji.USERNAME", query.getUser(), prms);
                wh += getStringPredicate("ji.SESSION_KEY", query.getSessionId(), prms);
                wh += getStatusPredicate("ji.STATUS", query.getStatus(), prms);

                wh += getStringPredicate("jd.JD_KEY", query.getApplicationName(), prms);
                wh += getStringPredicate("jd.APPLICATION", query.getJobDefApplication(), prms);
                wh += getStringPredicate("jd.MODULE", query.getJobDefModule(), prms);
                wh += getStringPredicate("jd.KEYWORD1", query.getJobDefKeyword1(), prms);
                wh += getStringPredicate("jd.KEYWORD2", query.getJobDefKeyword2(), prms);
                wh += getStringPredicate("jd.KEYWORD3", query.getJobDefKeyword3(), prms);

                wh += getStringPredicate("n.NAME", query.getNodeName(), prms);

                wh += getStringPredicate("q.NAME", query.getQueueName(), prms);
                wh += getIntPredicate("q.ID", query.getQueueId() == null ? null : query.getQueueId(), prms);

                wh += getCalendarPredicate("ji.DATE_ENQUEUE", query.getEnqueuedAfter(), ">=", prms);
                wh += getCalendarPredicate("ji.DATE_ENQUEUE", query.getEnqueuedBefore(), "<=", prms);
                wh += getCalendarPredicate("ji.DATE_START", query.getBeganRunningAfter(), ">=", prms);
                wh += getCalendarPredicate("ji.DATE_START", query.getBeganRunningBefore(), "<=", prms);

                q1 = "SELECT ji.ID, jd.APPLICATION AS JD_APPLICATION, jd.JD_KEY, ji.DATE_ATTRIBUTION, "
                        + "ji.EMAIL, NULL AS DATE_END, ji.DATE_ENQUEUE, ji.DATE_START, "
                        + "ji.HIGHLANDER, ji.APPLICATION AS INSTANCE_APPLICATION, ji.KEYWORD1 AS INSTANCE_KEYWORD1, "
                        + "ji.KEYWORD2 AS INSTANCE_KEYWORD2, ji.KEYWORD3 AS INSTANCE_KEYWORD3, ji.MODULE AS INSTANCE_MODULE, "
                        + "jd.KEYWORD1 AS JD_KEYWORD1, jd.KEYWORD2 AS JD_KEYWORD2, jd.KEYWORD3 AS JD_KEYWORD3, jd.MODULE AS JD_MODULE,"
                        + "n.NAME AS NODE_NAME, ji.PARENT AS PARENT, ji.PROGRESS, q.NAME AS QUEUE_NAME, NULL AS RETURN_CODE,"
                        + "ji.SESSION_KEY AS SESSION_KEY, ji.STATUS, ji.USERNAME, ji.JOBDEF, ji.NODE, ji.QUEUE, ji.INTERNAL_POSITION AS POSITION, ji.FROM_SCHEDULE, ji.PRIORITY, ji.DATE_NOT_BEFORE "
                        + "FROM __T__JOB_INSTANCE ji LEFT JOIN __T__QUEUE q ON ji.QUEUE=q.ID LEFT JOIN __T__JOB_DEFINITION jd ON ji.JOBDEF=jd.ID LEFT JOIN __T__NODE n ON ji.NODE=n.ID ";

                if (wh.length() > 3)
                {
                    wh = wh.substring(3, wh.length() - 1);
                    q1 += "WHERE " + wh;
                    filterCountQuery += String.format(
                            " (SELECT COUNT(1) FROM __T__JOB_INSTANCE ji LEFT JOIN __T__QUEUE q ON ji.QUEUE=q.ID LEFT JOIN __T__NODE n ON ji.NODE=n.ID LEFT JOIN __T__JOB_DEFINITION jd ON ji.JOBDEF=jd.ID WHERE %s) ,",
                            wh);
                }
                else
                {
                    filterCountQuery += " (SELECT COUNT(1) FROM __T__JOB_INSTANCE) ,";
                }
            }

            /////////////////////////////////////
            // HISTORY QUERY
            if (query.isQueryHistoryInstances())
            {
                wh = "";

                wh += getIntPredicate("ID", query.getJobInstanceId(), prms);
                wh += getIntPredicate("PARENT", query.getParentId(), prms);
                wh += getStringPredicate("INSTANCE_APPLICATION", query.getInstanceApplication(), prms);
                wh += getStringPredicate("INSTANCE_MODULE", query.getInstanceModule(), prms);
                wh += getStringPredicate("INSTANCE_KEYWORD1", query.getInstanceKeyword1(), prms);
                wh += getStringPredicate("INSTANCE_KEYWORD2", query.getInstanceKeyword2(), prms);
                wh += getStringPredicate("INSTANCE_KEYWORD3", query.getInstanceKeyword3(), prms);
                wh += getStringPredicate("USERNAME", query.getUser(), prms);
                wh += getStringPredicate("SESSION_KEY", query.getSessionId(), prms);
                wh += getStatusPredicate("STATUS", query.getStatus(), prms);

                wh += getStringPredicate("JD_KEY", query.getApplicationName(), prms);
                wh += getStringPredicate("JD_APPLICATION", query.getJobDefApplication(), prms);
                wh += getStringPredicate("JD_MODULE", query.getJobDefModule(), prms);
                wh += getStringPredicate("JD_KEYWORD1", query.getJobDefKeyword1(), prms);
                wh += getStringPredicate("JD_KEYWORD2", query.getJobDefKeyword2(), prms);
                wh += getStringPredicate("JD_KEYWORD3", query.getJobDefKeyword3(), prms);

                wh += getStringPredicate("NODE_NAME", query.getNodeName(), prms);

                wh += getStringPredicate("QUEUE_NAME", query.getQueueName(), prms);
                wh += getIntPredicate("QUEUE", query.getQueueId() == null ? null : query.getQueueId(), prms);

                wh += getCalendarPredicate("DATE_ENQUEUE", query.getEnqueuedAfter(), ">=", prms);
                wh += getCalendarPredicate("DATE_ENQUEUE", query.getEnqueuedBefore(), "<=", prms);
                wh += getCalendarPredicate("DATE_START", query.getBeganRunningAfter(), ">=", prms);
                wh += getCalendarPredicate("DATE_START", query.getBeganRunningBefore(), "<=", prms);
                wh += getCalendarPredicate("DATE_END", query.getEndedAfter(), ">=", prms);
                wh += getCalendarPredicate("DATE_END", query.getEndedBefore(), "<=", prms);

                q2 += "SELECT ID, JD_APPLICATION, JD_KEY, DATE_ATTRIBUTION, EMAIL, "
                        + "DATE_END, DATE_ENQUEUE, DATE_START, HIGHLANDER, INSTANCE_APPLICATION, "
                        + "INSTANCE_KEYWORD1, INSTANCE_KEYWORD2, INSTANCE_KEYWORD3, INSTANCE_MODULE, "
                        + "JD_KEYWORD1, JD_KEYWORD2, JD_KEYWORD3, " + "JD_MODULE, NODE_NAME, PARENT, PROGRESS, QUEUE_NAME, "
                        + "RETURN_CODE, SESSION_KEY, STATUS, USERNAME, JOBDEF, NODE, QUEUE, 0 as POSITION, FROM_SCHEDULE, PRIORITY AS PRIORITY, DATE_NOT_BEFORE FROM __T__HISTORY ";

                if (wh.length() > 3)
                {
                    wh = wh.substring(3, wh.length() - 1);
                    q2 += "WHERE " + wh;
                    filterCountQuery += String.format(" (SELECT COUNT(1) FROM __T__HISTORY WHERE %s) ,", wh);
                }
                else
                {
                    filterCountQuery += " (SELECT COUNT(1) FROM __T__HISTORY) ,";
                }
            }

            ///////////////////////////////////////////////
            // UNION
            if (q1.isEmpty())
            {
                q = q2;
            }
            else if (q2.isEmpty())
            {
                q = q1;
            }
            else
            {
                q = String.format("(%s) UNION ALL (%s) ", q1, q2);
            }

            ///////////////////////////////////////////////
            // Sort (on the union, not the sub queries)
            String sort = "";
            for (SortSpec s : query.getSorts())
            {
                if (query.isQueryLiveInstances() && !query.isQueryHistoryInstances() && s.col == Sort.DATEEND)
                {
                    closeQuietly(cnx); // Not needed but linter bug
                    throw new JqmInvalidRequestException("cannot sort live instances by end date as those instances are still running");
                }

                sort += "," + s.col.getHistoryField() + " " + (s.order == SortOrder.ASCENDING ? "ASC" : "DESC");
            }
            if (sort.isEmpty())
            {
                sort = " ORDER BY ID";
            }
            else
            {
                sort = " ORDER BY " + sort.substring(1);
            }
            q += sort;

            ///////////////////////////////////////////////
            // Set pagination parameters
            List<Object> paginatedParameters = new ArrayList<>(prms);
            if (query.getFirstRow() != null || query.getPageSize() != null)
            {
                int start = query.getFirstRow() != null ? query.getFirstRow() : 0;
                int end = query.getPageSize() != null ? start + query.getPageSize() : Integer.MAX_VALUE;
                q = cnx.paginateQuery(q, start, end, paginatedParameters);
            }

            ///////////////////////////////////////////////
            // Run the query
            ResultSet rs = cnx.runRawSelect(q, paginatedParameters.toArray());
            while (rs.next())
            {
                com.enioka.jqm.client.api.JobInstance tmp = getJob(rs, cnx);
                res.put(tmp.getId(), tmp);
            }
            rs.close();
            jqmlogger.debug("Free query has returned row count " + res.size());

            // If needed, fetch the total result count (without pagination). Note that without pagination, the Query object does not need
            // this indication.
            if (query.getFirstRow() != null || (query.getPageSize() != null && res.size() >= query.getPageSize()))
            {
                ResultSet rs2 = cnx.runRawSelect(filterCountQuery.substring(0, filterCountQuery.length() - 2) + " AS D FROM (VALUES(0))",
                        prms.toArray());
                rs2.next();

                query.setResultSize(rs2.getInt(1));
            }

            ///////////////////////////////////////////////
            // Fetch messages and parameters in batch

            // Optimization: fetch messages and parameters in batches of 50 (limit accepted by most databases for IN clauses).
            List<List<Integer>> ids = new ArrayList<>();
            List<Integer> currentList = null;
            int i = 0;
            for (com.enioka.jqm.client.api.JobInstance ji : res.values())
            {
                if (currentList == null || i % IN_CLAUSE_LIMIT == 0)
                {
                    currentList = new ArrayList<>(IN_CLAUSE_LIMIT);
                    ids.add(currentList);
                }
                currentList.add(ji.getId());
                i++;
            }
            if (currentList != null && !currentList.isEmpty())
            {
                for (List<Integer> idsBatch : ids)
                {
                    ResultSet run = cnx.runSelect("jiprm_select_by_ji_list", idsBatch);
                    while (run.next())
                    {
                        res.get(run.getInt(2)).getParameters().put(run.getString(3), run.getString(4));
                    }
                    run.close();

                    ResultSet msg = cnx.runSelect("message_select_by_ji_list", idsBatch);
                    while (msg.next())
                    {
                        res.get(msg.getInt(2)).getMessages().add(msg.getString(3));
                    }
                    run.close();
                }
            }

            ///////////////////////////////////////////////
            // DONE AT LAST
            query.setResults(new ArrayList<>(res.values()));
            return query.getResults();
        }
        catch (Exception e)
        {
            throw new JqmClientException("an error occured during query execution", e);
        }
    }

    private com.enioka.jqm.client.api.JobInstance getJob(ResultSet rs, DbConn cnx) throws SQLException
    {
        com.enioka.jqm.client.api.JobInstance res = new com.enioka.jqm.client.api.JobInstance();

        res.setId(rs.getInt(1));
        // res.setApplication(rs.getString(2));
        res.setApplicationName(rs.getString(3));
        res.setEmail(rs.getString(5));
        res.setEndDate(cnx.getCal(rs, 6));
        res.setEnqueueDate(cnx.getCal(rs, 7));
        res.setBeganRunningDate(cnx.getCal(rs, 8));
        res.setHighlander(rs.getBoolean(9));
        res.setApplication(rs.getString(10));
        res.setKeyword1(rs.getString(11));
        res.setKeyword2(rs.getString(12));
        res.setKeyword3(rs.getString(13));
        res.setModule(rs.getString(14));
        res.setDefinitionKeyword1(rs.getString(15));
        res.setDefinitionKeyword2(rs.getString(16));
        res.setDefinitionKeyword3(rs.getString(17));
        res.setNodeName(rs.getString(19));
        res.setParent(rs.getInt(20));
        res.setProgress(rs.getInt(21));
        res.setQueueName(rs.getString(22));
        res.setSessionID(rs.getString(24));
        res.setState(com.enioka.jqm.client.api.State.valueOf(rs.getString(25)));
        res.setUser(rs.getString(26));

        com.enioka.jqm.client.api.Queue q = new com.enioka.jqm.client.api.Queue();
        q.setId(rs.getInt(29));
        q.setName(rs.getString(22));
        res.setQueue(q);

        res.setPosition(rs.getLong(30));
        res.setFromSchedule(rs.getBoolean(31));
        res.setPriority(rs.getInt(32) > 0 ? rs.getInt(32) : null);

        res.setRunAfter(cnx.getCal(rs, 33));

        return res;
    }

    @Override
    public com.enioka.jqm.client.api.JobInstance getJob(int idJob)
    {
        // TODO: direct queries following previous logic, but after we have common table structures.
        return this.newQuery().setJobInstanceId(idJob).setQueryHistoryInstances(true).setQueryLiveInstances(true).invoke().get(0);

        // DbConn cnx = null;
        // try
        // {
        // // Three steps: first, query History as:
        // // * this is supposed to be the most frequent query.
        // // * we try to avoid hitting the queues if possible
        // // Second, query live queues
        // // Third, query history again (because a JI may have ended between the first two queries, so we may miss a JI)
        // // Outside this case, this third query will be very rare, as the method is always called with an ID that cannot be
        // // guessed as its only parameter, so the existence of the JI is nearly always a given.
        // cnx = getDbSession();
        // History h = em.find(History.class, idJob);
        // com.enioka.jqm.api.JobInstance res = null;
        // if (h != null)
        // {
        // res = getJob(h, em);
        // }
        // else
        // {
        // JobInstance ji = em.find(JobInstance.class, idJob);
        // if (ji != null)
        // {
        // res = getJob(ji, em);
        // }
        // else
        // {
        // h = em.find(History.class, idJob);
        // if (h != null)
        // {
        // res = getJob(h, em);
        // }
        // else
        // {
        // throw new JqmInvalidRequestException("No job instance of ID " + idJob);
        // }
        // }
        // }
        // return res;
        // }
        // catch (JqmInvalidRequestException e)
        // {
        // throw e;
        // }
        // catch (Exception e)
        // {
        // throw new JqmClientException("an error occured during query execution", e);
        // }
        // finally
        // {
        // closeQuietly(em);
        // }
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getJobs()
    {
        return this.newQuery().setQueryHistoryInstances(true).setQueryLiveInstances(true).invoke();
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getActiveJobs()
    {
        return this.newQuery().setQueryHistoryInstances(false).setQueryLiveInstances(true).addSortAsc(Sort.ID).invoke();
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getUserActiveJobs(String user)
    {
        if (user == null || user.isEmpty())
        {
            throw new JqmInvalidRequestException("user cannot be null or empty");
        }

        return this.newQuery().setUser(user).setQueryHistoryInstances(false).setQueryLiveInstances(true).addSortAsc(Sort.ID).invoke();
    }

    // /////////////////////////////////////////////////////////////////////
    // Helpers to quickly access some job instance properties
    // /////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getJobMessages(int idJob)
    {
        return getJob(idJob).getMessages();
    }

    @Override
    public int getJobProgress(int idJob)
    {
        return getJob(idJob).getProgress();
    }

    // /////////////////////////////////////////////////////////////////////
    // Deliverables retrieval
    // /////////////////////////////////////////////////////////////////////

    @Override
    public List<com.enioka.jqm.client.api.Deliverable> getJobDeliverables(int idJob)
    {
        try (DbConn cnx = getDbSession())
        {

            // TODO: no intermediate entity here: directly SQL => API object.
            List<Deliverable> deliverables = Deliverable.select(cnx, "deliverable_select_all_for_ji", idJob);
            List<com.enioka.jqm.client.api.Deliverable> res = new ArrayList<>();
            for (Deliverable d : deliverables)
            {
                res.add(new com.enioka.jqm.client.api.Deliverable(d.getFilePath(), d.getFileFamily(), d.getId(), d.getOriginalFileName()));
            }

            return res;
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not query files for job instance " + idJob, e);
        }
    }

    @Override
    public List<InputStream> getJobDeliverablesContent(int idJob)
    {
        ArrayList<InputStream> streams = new ArrayList<>();
        try (DbConn cnx = getDbSession())
        {
            for (Deliverable del : Deliverable.select(cnx, "deliverable_select_all_for_ji", idJob))
            {
                streams.add(getDeliverableContent(del));
            }
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not retrieve file streams", e);
        }
        return streams;
    }

    @Override
    public InputStream getDeliverableContent(com.enioka.jqm.client.api.Deliverable d)
    {
        return getDeliverableContent(d.getId());
    }

    @Override
    public InputStream getDeliverableContent(int delId)
    {
        Deliverable deliverable = null;

        try (DbConn cnx = getDbSession())
        {
            List<Deliverable> dd = Deliverable.select(cnx, "deliverable_select_by_id", delId);
            if (dd.size() == 0)
            {
                throw new JqmClientException("There is no deliverable with the given ID - check your ID");
            }
            deliverable = dd.get(0);
        }
        catch (Exception e)
        {
            throw new JqmInvalidRequestException("Could not get find deliverable description inside DB - your ID may be wrong", e);
        }

        return getDeliverableContent(deliverable);
    }

    public InputStream getEngineLog(String nodeName, int latest)
    {
        URL url = null;

        try (DbConn cnx = getDbSession())
        {
            ResultSet rs = cnx.runSelect("node_select_connectdata_by_key", nodeName);
            if (!rs.next())
            {
                throw new NoResultException("no node named " + nodeName);
            }
            String dns = rs.getString(1);
            Integer port = rs.getInt(2);

            if (rs.next())
            {
                throw new NonUniqueResultException("configuration issue: multiple nodes named " + nodeName);
            }

            url = new URL(getFileProtocol(cnx) + dns + ":" + port + "/ws/simple/enginelog?latest=" + latest);
            jqmlogger.trace("Will invoke engine log URL: " + url.toString());
        }
        catch (MalformedURLException e)
        {
            throw new JqmInvalidRequestException("URL is not valid " + url);
        }
        catch (NoResultException e)
        {
            throw new JqmInvalidRequestException("Node with name " + nodeName + " does not exist");
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not process request", e);
        }

        return getFile(url.toString());
    }

    // Helper
    private InputStream getDeliverableContent(Deliverable deliverable)
    {
        URL url = null;
        try
        {
            String uriStart = getHostForLaunch(deliverable.getJobId());
            url = new URL(uriStart + "/ws/simple/file?id=" + deliverable.getRandomId());
            jqmlogger.trace("URL: " + url.toString());
        }
        catch (MalformedURLException e)
        {
            throw new JqmClientException("URL is not valid " + url, e);
        }

        return getFile(url.toString());
    }

    private String getFileProtocol(DbConn cnx)
    {
        if (protocol == null)
        {
            protocol = "http://";
            try
            {
                String prm = GlobalParameter.getParameter(cnx, "enableWsApiSsl", "false");
                if (Boolean.parseBoolean(prm))
                {
                    protocol = "https://";
                }
            }
            catch (NoResultException e)
            {
                protocol = "http://";
            }
        }
        return protocol;
    }

    private InputStream getFile(String url)
    {
        File file = null;
        String nameHint = null;

        File destDir = new File(System.getProperty("java.io.tmpdir"));
        if (!destDir.isDirectory() && !destDir.mkdir())
        {
            throw new JqmClientException("could not create temp directory " + destDir.getAbsolutePath());
        }
        jqmlogger.trace("File will be copied into " + destDir);

        try (DbConn cnx = getDbSession())
        {
            file = new File(destDir + "/" + UUID.randomUUID().toString());

            CredentialsProvider credsProvider = null;
            if (SimpleApiSecurity.getId(cnx).usr != null)
            {
                credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(SimpleApiSecurity.getId(cnx).usr, SimpleApiSecurity.getId(cnx).pass));
            }
            SSLContext ctx = null;
            if (getFileProtocol(cnx).equals("https://"))
            {
                try
                {
                    if (p.containsKey("com.enioka.jqm.ws.truststoreFile"))
                    {
                        KeyStore trust = null;

                        try
                        {
                            trust = KeyStore.getInstance(this.p.getProperty("com.enioka.jqm.ws.truststoreType", "JKS"));
                        }
                        catch (KeyStoreException e)
                        {
                            throw new JqmInvalidRequestException("Specified trust store type ["
                                    + this.p.getProperty("com.enioka.jqm.ws.truststoreType", "JKS") + "] is invalid", e);
                        }

                        try (InputStream trustIs = new FileInputStream(this.p.getProperty("com.enioka.jqm.ws.truststoreFile")))
                        {

                            String trustp = this.p.getProperty("com.enioka.jqm.ws.truststorePass", null);
                            trust.load(trustIs, (trustp == null ? null : trustp.toCharArray()));
                        }
                        catch (FileNotFoundException e)
                        {
                            throw new JqmInvalidRequestException(
                                    "Trust store file [" + this.p.getProperty("com.enioka.jqm.ws.truststoreFile") + "] cannot be found", e);
                        }
                        catch (Exception e)
                        {
                            throw new JqmInvalidRequestException("Could not load the trust store file", e);
                        }

                        ctx = SSLContexts.custom().loadTrustMaterial(trust, null).build();
                    }
                    else
                    {
                        ctx = SSLContexts.createSystemDefault();
                    }
                }
                catch (Exception e)
                {
                    // Cannot happen - not trust store is actually loaded!
                    jqmlogger.error("An supposedly impossible error has happened. Downloading files through the API may not work.", e);
                }
            }

            // CloseableHttpResponse rs = null;
            try (CloseableHttpClient cl = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).setSSLContext(ctx).build())
            {
                // Run HTTP request
                HttpUriRequest rq = new HttpGet(url.toString());
                try (CloseableHttpResponse rs = cl.execute(rq))
                {
                    if (rs.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                    {
                        throw new JqmClientException(
                                "Could not retrieve file from JQM node. The file may have been purged, or the node may be unreachable. HTTP code was: "
                                        + rs.getStatusLine().getStatusCode());
                    }

                    // There may be a filename hint inside the response
                    Header[] hs = rs.getHeaders("Content-Disposition");
                    if (hs.length == 1)
                    {
                        Header h = hs[0];
                        if (h.getValue().contains("filename="))
                        {
                            nameHint = h.getValue().split("=")[1];
                        }
                    }

                    try (FileOutputStream fos = new FileOutputStream(file))
                    {
                        // Save the file to a temp local file
                        rs.getEntity().writeTo(fos);
                        jqmlogger.trace("File was downloaded to " + file.getAbsolutePath());
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new JqmClientException("Could not create a webserver-local copy of the file. The remote node may be down. " + url, e);
        }

        SelfDestructFileStream res = null;
        try
        {
            res = new SelfDestructFileStream(file);
        }
        catch (IOException e)
        {
            throw new JqmClientException("File seems not to be present where it should have been downloaded", e);
        }
        res.nameHint = nameHint;
        return res;
    }

    @Override
    public InputStream getJobLogStdOut(int jobId)
    {
        return getJobLog(jobId, ".stdout", "stdout");
    }

    @Override
    public InputStream getJobLogStdErr(int jobId)
    {
        return getJobLog(jobId, ".stderr", "stderr");
    }

    private String getHostForLaunch(int launchId)
    {
        String host;
        try (DbConn cnx = getDbSession())
        {
            try
            {
                host = cnx.runSelectSingle("history_select_cnx_data_by_id", String.class, launchId);
            }
            catch (NoResultException e)
            {
                try
                {
                    host = cnx.runSelectSingle("ji_select_cnx_data_by_id", String.class, launchId);
                }
                catch (NoResultException r)
                {
                    throw new JqmInvalidRequestException("No ended or running job instance found for this file");
                }
            }

            if (host == null || host.isEmpty() || host.split(":")[0].isEmpty())
            {
                throw new JqmInvalidRequestException("cannot retrieve a file from a deleted node");
            }

            protocol = getFileProtocol(cnx);
            return protocol + host;
        }
        catch (JqmException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmClientException("Could not process request", e);
        }
    }

    private InputStream getJobLog(int jobId, String extension, String param)
    {
        // 1: retrieve node to address
        String uriStart = getHostForLaunch(jobId);

        // 2: build URL
        URL url = null;
        try
        {
            url = new URL(uriStart + "/ws/simple/" + param + "?id=" + jobId);
            jqmlogger.trace("URL: " + url.toString());
        }
        catch (MalformedURLException e)
        {
            throw new JqmClientException("URL is not valid " + url, e);
        }

        return getFile(url.toString());
    }

    // /////////////////////////////////////////////////////////////////////
    // Queue APIs
    // /////////////////////////////////////////////////////////////////////

    @Override
    public List<com.enioka.jqm.client.api.Queue> getQueues()
    {
        List<com.enioka.jqm.client.api.Queue> res = new ArrayList<>();
        com.enioka.jqm.client.api.Queue tmp = null;

        try (DbConn cnx = getDbSession())
        {
            for (Queue q : Queue.select(cnx, "q_select_all"))
            {
                tmp = getQueue(q);
                res.add(tmp);
            }
            return res;
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not query queues", e);
        }
    }

    @Override
    public void pauseQueue(com.enioka.jqm.client.api.Queue q)
    {
        try (DbConn cnx = getDbSession())
        {
            cnx.runUpdate("dp_update_enable_by_queue_id", Boolean.FALSE, q.getId());
            cnx.commit();
            jqmlogger.info("Queue {} has been paused", q.getId());
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not pause queue", e);
        }
    }

    @Override
    public void resumeQueue(com.enioka.jqm.client.api.Queue q)
    {
        try (DbConn cnx = getDbSession())
        {
            cnx.runUpdate("dp_update_enable_by_queue_id", Boolean.TRUE, q.getId());
            cnx.commit();
            jqmlogger.info("Queue {} has been resumed", q.getId());
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not pause queue", e);
        }
    }

    @Override
    public void clearQueue(com.enioka.jqm.client.api.Queue q)
    {
        try (DbConn cnx = getDbSession())
        {
            QueryResult qr = cnx.runUpdate("ji_delete_waiting_in_queue_id", q.getId());
            cnx.commit();
            jqmlogger.info("{} waiting job instances were removed from queue {}", qr.nbUpdated, q.getId());
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not clear queue", e);
        }
    }

    @Override
    public QueueStatus getQueueStatus(com.enioka.jqm.client.api.Queue q)
    {
        try (DbConn cnx = getDbSession())
        {
            ResultSet rs = cnx.runSelect("dp_select_enabled_for_queue", q.getId());

            int nbEnabled = 0, nbDisabled = 0;
            while (rs.next())
            {
                boolean enabled = rs.getBoolean(1);
                int nbThreads = rs.getInt(2);

                if (!enabled || nbThreads == 0)
                {
                    nbDisabled++;
                }
                else
                {
                    nbEnabled++;
                }
            }

            if (nbDisabled > 0 && nbEnabled > 0)
            {
                return QueueStatus.PARTIALLY_RUNNING;
            }
            else if (nbDisabled > 0 || (nbDisabled == 0 && nbEnabled == 0))
            {
                return QueueStatus.PAUSED;
            }
            else
            {
                return QueueStatus.RUNNING;
            }
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not query queue status", e);
        }
    }

    @Override
    public int getQueueEnabledCapacity(com.enioka.jqm.client.api.Queue q)
    {
        int capacity = 0;
        try (DbConn cnx = getDbSession())
        {
            ResultSet rs = cnx.runSelect("dp_select_sum_queue_capacity", q.getId());

            while (rs.next())
            {
                capacity = rs.getInt(1);
            }
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not query queue capacity around nodes", e);
        }

        return capacity;
    }

    // /////////////////////////////////////////////////////////////////////
    // Parameters retrieval
    // /////////////////////////////////////////////////////////////////////

    private static com.enioka.jqm.client.api.Queue getQueue(Queue queue)
    {
        com.enioka.jqm.client.api.Queue q = new com.enioka.jqm.client.api.Queue();

        q.setDescription(queue.getDescription());
        q.setId(queue.getId());
        q.setName(queue.getName());

        return q;
    }

    private static Schedule getSchedule(ScheduledJob s, Map<Integer, com.enioka.jqm.client.api.Queue> queues)
    {
        Schedule res = new Schedule();
        res.setCronExpression(s.getCronExpression());
        res.setId(s.getId());
        res.setPriority(s.getPriority());
        res.setQueue(s.getQueue() == null ? null : queues.get(s.getQueue()));
        res.setParameters(s.getParameters());

        return res;
    }

    @Override
    public List<com.enioka.jqm.client.api.JobDef> getJobDefinitions()
    {
        return getJobDefinitionsInternal("jd_select_all");
    }

    @Override
    public List<com.enioka.jqm.client.api.JobDef> getJobDefinitions(String application)
    {
        return getJobDefinitionsInternal("jd_select_by_tag_app", application);
    }

    @Override
    public com.enioka.jqm.client.api.JobDef getJobDefinition(String name)
    {
        List<com.enioka.jqm.client.api.JobDef> res = getJobDefinitionsInternal("jd_select_by_key", name);
        if (res.isEmpty())
        {
            throw new JqmInvalidRequestException("No job definition named " + name);
        }
        return res.get(0);
    }

    private List<com.enioka.jqm.client.api.JobDef> getJobDefinitionsInternal(String queryName, String... args)
    {
        List<com.enioka.jqm.client.api.JobDef> res = new ArrayList<>();
        List<JobDef> dbr = null;
        List<Integer> ids = null;
        Map<Integer, com.enioka.jqm.client.api.Queue> queues = null;
        Map<Integer, List<JobDefParameter>> allParams = null;
        List<ScheduledJob> sjs = null;

        try (DbConn cnx = getDbSession())
        {
            // TODO: remove model objects and go directly from RS to API objects. Also, join to avoid multiple queries.

            dbr = JobDef.select(cnx, queryName, (Object[]) args);

            if (!dbr.isEmpty())
            {
                queues = new HashMap<>();
                for (com.enioka.jqm.client.api.Queue q : getQueues())
                {
                    queues.put(q.getId(), q);
                }

                ids = new ArrayList<>();
                for (JobDef jd : dbr)
                {
                    ids.add(jd.getId());
                }
                sjs = ScheduledJob.select(cnx, "sj_select_for_jd_list", ids);
                allParams = JobDefParameter.select_all(cnx, "jdprm_select_all_for_jd_list", ids);
            }

            for (JobDef jd : dbr)
            {
                com.enioka.jqm.client.api.JobDef tmp = new com.enioka.jqm.client.api.JobDef();

                // Basic fields
                tmp.setApplication(jd.getApplication());
                tmp.setApplicationName(jd.getApplicationName());
                tmp.setCanBeRestarted(jd.isCanBeRestarted());
                tmp.setDescription(jd.getDescription());
                tmp.setHighlander(jd.isHighlander());
                tmp.setKeyword1(jd.getKeyword1());
                tmp.setKeyword2(jd.getKeyword2());
                tmp.setKeyword3(jd.getKeyword3());
                tmp.setModule(jd.getModule());
                tmp.setId(jd.getId());

                tmp.setQueue(queues.get(jd.getQueue()));

                // Parameters
                List<JobDefParameter> parameters = allParams.get(jd.getId());
                if (parameters != null)
                {
                    for (JobDefParameter jdf : parameters)
                    {
                        tmp.addParameter(jdf.getKey(), jdf.getValue());
                    }
                }

                // Schedules
                for (ScheduledJob s : sjs)
                {
                    if (s.getJobDefinition() == jd.getId())
                    {
                        tmp.addSchedule(getSchedule(s, queues));
                    }
                }

                res.add(tmp);
            }
            return res;
        }
        catch (Exception e)
        {
            throw new JqmClientException("could not query JobDef", e);
        }
    }
}
