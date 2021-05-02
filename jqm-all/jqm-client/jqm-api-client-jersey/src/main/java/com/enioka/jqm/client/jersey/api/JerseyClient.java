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

package com.enioka.jqm.client.jersey.api;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;

import com.enioka.jqm.client.api.Deliverable;
import com.enioka.jqm.client.api.JobDef;
import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.client.api.JqmClientException;
import com.enioka.jqm.client.api.JqmInvalidRequestException;
import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.api.Queue;
import com.enioka.jqm.client.api.QueueStatus;
import com.enioka.jqm.client.shared.JobRequestBaseImpl;
import com.enioka.jqm.client.shared.JqmClientEnqueueCallback;
import com.enioka.jqm.client.shared.JqmClientQuerySubmitCallback;
import com.enioka.jqm.client.shared.QueryBaseImpl;

import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main JQM client API entry point.
 */
final class JerseyClient implements JqmClient, JqmClientQuerySubmitCallback, JqmClientEnqueueCallback
{
    private static Logger jqmlogger = LoggerFactory.getLogger(JerseyClient.class);

    private Properties p;
    private Client client;
    private WebTarget target;

    ///////////////////////////////////////////////////////////////////////
    // Construction/Connection
    ///////////////////////////////////////////////////////////////////////

    // No public constructor. MUST use factory.
    JerseyClient(Properties p)
    {
        jqmlogger.trace("creating a new WS client");

        ///////////////////////////////////////
        // Property loading

        // Properties priority is: Given explicitly > Given as sys param > Given inside conf file.
        // They are therefore loaded in that reverse order.
        this.p = new Properties();

        // Configuration file
        InputStream fis = null;
        try
        {
            fis = this.getClass().getClassLoader().getResourceAsStream("jqm.properties");
            if (fis != null)
            {
                jqmlogger.info("Loading jqm properties file");
                this.p.load(fis);
            }
            else
            {
                jqmlogger.info("No jqm properties file for WS client found in class path");
            }
        }
        catch (Exception e)
        {
            throw new JqmClientException("An error occurred during jqm.properties file search or load", e);
        }
        finally
        {
            try
            {
                fis.close();
            }
            catch (Exception e)
            {
                // Ignore.
            }
        }

        // System parameter (only for URL, not documented nor supported by the way)
        if (System.getProperty("com.enioka.jqm.ws.url") != null)
        {
            this.p.setProperty("com.enioka.jqm.ws.url", System.getProperty("com.enioka.jqm.ws.url"));
        }

        // Given explicitly
        this.p.putAll(p);

        ///////////////////////////////////////
        // SSL certificates
        if (this.p.containsKey("com.enioka.jqm.ws.truststoreFile"))
        {
            jqmlogger.info("A trustore was specified and will be loaded");
            ClientBuilder bld = ClientBuilder.newBuilder();
            KeyStore trust = null;
            InputStream trustIs = null;

            try
            {
                trust = KeyStore.getInstance(this.p.getProperty("com.enioka.jqm.ws.truststoreType", "JKS"));
            }
            catch (KeyStoreException e)
            {
                throw new JqmInvalidRequestException(
                        "Specified trust store type [" + this.p.getProperty("com.enioka.jqm.ws.truststoreType", "JKS") + "] is invalid", e);
            }

            try
            {
                trustIs = new FileInputStream(this.p.getProperty("com.enioka.jqm.ws.truststoreFile"));
            }
            catch (FileNotFoundException e)
            {
                throw new JqmInvalidRequestException(
                        "Trust store file [" + this.p.getProperty("com.enioka.jqm.ws.truststoreFile") + "] cannot be found", e);
            }

            String trustp = this.p.getProperty("com.enioka.jqm.ws.truststorePass", null);
            try
            {
                trust.load(trustIs, (trustp == null ? null : trustp.toCharArray()));
            }
            catch (Exception e)
            {
                throw new JqmInvalidRequestException("Could not load the trust store file", e);
            }
            finally
            {
                try
                {
                    trustIs.close();
                }
                catch (IOException e)
                {
                    // Nothing to do.
                }
            }

            bld.trustStore(trust);

            // Client certificate
            if (this.p.containsKey("com.enioka.jqm.ws.keystoreFile"))
            {
                jqmlogger.info("A keystore was specified and will be loaded");
                KeyStore keyStore = null;
                InputStream keyIs = null;

                try
                {
                    keyStore = KeyStore.getInstance(this.p.getProperty("com.enioka.jqm.ws.keystoreType", "JKS"));
                }
                catch (KeyStoreException e)
                {
                    throw new JqmInvalidRequestException(
                            "Specified key store type [" + this.p.getProperty("com.enioka.jqm.ws.keystoreType", "JKS") + "] is invalid", e);
                }

                try
                {
                    keyIs = new FileInputStream(this.p.getProperty("com.enioka.jqm.ws.keystoreFile"));
                }
                catch (FileNotFoundException e)
                {
                    throw new JqmInvalidRequestException(
                            "Key store file [" + this.p.getProperty("com.enioka.jqm.ws.keystoreFile") + "] cannot be found", e);
                }

                String keyp = this.p.getProperty("com.enioka.jqm.ws.keystorePass", null);
                try
                {
                    keyStore.load(keyIs, (keyp == null ? null : keyp.toCharArray()));
                }
                catch (Exception e)
                {
                    throw new JqmInvalidRequestException("Could not load the key store file", e);
                }
                finally
                {
                    try
                    {
                        keyIs.close();
                    }
                    catch (IOException e)
                    {
                        // Nothing to do.
                    }
                }

                bld.keyStore(keyStore, keyp);
            }

            client = bld.build();
        }
        else
        {
            // No trust store given = no SSL allowed => default client
            client = ClientBuilder.newClient();
        }

        // Basic Authentication (only allowed when no client certificate is given)
        if (this.p.containsKey("com.enioka.jqm.ws.login") && this.p.containsKey("com.enioka.jqm.ws.password")
                && !this.p.containsKey("com.enioka.jqm.ws.keystoreFile"))
        {
            jqmlogger.info("A login/password pair was specified and will be used");
            HttpAuthenticationFeature auth = HttpAuthenticationFeature.basic(this.p.getProperty("com.enioka.jqm.ws.login"),
                    this.p.getProperty("com.enioka.jqm.ws.password"));
            client.register(auth);
        }

        ///////////////////////////////////////
        // Create client
        String url = this.p.getProperty("com.enioka.jqm.ws.url", "http://localhost:1789/ws/client");
        jqmlogger.debug("The following root service URL will be used: " + url);
        this.target = client.target(url);
    }

    @Override
    public void dispose()
    {
        p = null;
        this.client.close();
    }

    ///////////////////////////////////////////////////////////////////////
    // Enqueue functions
    ///////////////////////////////////////////////////////////////////////

    @Override
    public JobRequestBaseImpl newJobRequest(String applicationName, String user)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public int enqueue(JobRequestBaseImpl jd)
    {
        try
        {
            return target.path("ji").request().post(Entity.entity(jd, MediaType.APPLICATION_XML), JobInstance.class).getId();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public int enqueue(String applicationName, String userName)
    {
        return enqueue(newJobRequest(applicationName, userName));
    }

    @Override
    public int enqueueFromHistory(int jobIdToCopy)
    {
        JobInstance h = getJob(jobIdToCopy);
        JobRequest jd = newJobRequest(h.getApplication(), h.getUser());
        jd.setApplication(h.getApplication());
        jd.setApplicationName(h.getApplicationName());
        jd.setEmail(h.getEmail());
        jd.setKeyword1(h.getKeyword1());
        jd.setKeyword2(h.getKeyword2());
        jd.setKeyword3(h.getKeyword3());
        jd.setModule(h.getModule());
        jd.setParentID(h.getParent());
        jd.setSessionID(h.getSessionID());
        jd.setUser(h.getUser());

        for (Map.Entry<String, String> p : h.getParameters().entrySet())
        {
            jd.addParameter(p.getKey(), p.getValue());
        }

        return jd.enqueue();
    }

    ///////////////////////////////////////////////////////////////////////
    // Job destruction
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void cancelJob(int idJob)
    {
        try
        {
            target.path("ji/cancelled/" + idJob).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void deleteJob(int idJob)
    {
        try
        {
            target.path("ji/waiting/" + idJob).request().delete();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void killJob(int idJob)
    {
        try
        {
            target.path("ji/killed/" + idJob).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void removeRecurrence(int scheduleId)
    {
        try
        {
            target.path("schedule/" + scheduleId).request().delete();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }

    }

    ///////////////////////////////////////////////////////////////////////
    // Job Pause/restart
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void pauseQueuedJob(int idJob)
    {
        try
        {
            target.path("ji/paused/" + idJob).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void resumeQueuedJob(int idJob)
    {
        try
        {
            target.path("ji/paused/" + idJob).request().delete();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void resumeJob(int jobId)
    {
        resumeQueuedJob(jobId);
    }

    public int restartCrashedJob(int idJob)
    {
        try
        {
            return target.path("ji/crashed/" + idJob).request().delete(JobInstance.class).getId();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void pauseRunningJob(int jobId)
    {
        try
        {
            target.path("ji/running/paused/" + jobId).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void resumeRunningJob(int jobId)
    {
        try
        {
            target.path("ji/running/paused/" + jobId).request().delete();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Misc.
    ///////////////////////////////////////////////////////////////////////

    @Override
    public void setJobQueue(int idJob, int idQueue)
    {
        try
        {
            target.path("q/" + idQueue + "/" + idJob).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
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
        try
        {
            target.path("ji/" + idJob + "/position/" + position).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void setJobPriority(int jobId, int priority)
    {
        try
        {
            target.path("ji/" + jobId + "/priority/" + priority).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void setJobRunAfter(int jobId, Calendar whenToRun)
    {
        try
        {
            target.path("ji/" + jobId + "/delay/" + whenToRun.getTimeInMillis()).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void setScheduleQueue(int scheduleId, int queueId)
    {
        try
        {
            target.path("schedule/" + scheduleId + "/queue/" + queueId).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void setScheduleRecurrence(int scheduleId, String cronExpression)
    {
        try
        {
            target.path("schedule/" + scheduleId + "/cron/" + cronExpression).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void setSchedulePriority(int scheduleId, int priority)
    {
        try
        {
            target.path("schedule/" + scheduleId + "/priority/" + priority).request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Job queries
    ///////////////////////////////////////////////////////////////////////

    @Override
    public com.enioka.jqm.client.api.JobInstance getJob(int idJob)
    {
        try
        {
            return target.path("ji/" + idJob).request().get(JobInstance.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException("An internal JQM error occured", e);
        }
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getJobs()
    {
        try
        {
            return target.path("ji").request().get(new GenericType<List<JobInstance>>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getActiveJobs()
    {
        try
        {
            return target.path("ji/active").request().get(new GenericType<List<JobInstance>>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public List<com.enioka.jqm.client.api.JobInstance> getUserActiveJobs(String user)
    {
        try
        {
            return target.path("user/" + user + "/ji").request().get(new GenericType<List<JobInstance>>()
            {
            });
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public Query newQuery()
    {
        return new QueryBaseImpl(this);
    }

    @Override
    public List<JobInstance> getJobs(QueryBaseImpl query)
    {
        QueryBaseImpl query2 = (QueryBaseImpl) query;
        try
        {
            QueryBaseImpl res = target.path("ji/query").request().post(Entity.entity(query2, MediaType.APPLICATION_XML),
                    QueryBaseImpl.class);
            query2.setResultSize(res.getResultSize());
            query2.setResults(res.getResults());
            return res.getResults();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Helpers to quickly access some job instance properties
    ///////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getJobMessages(int idJob)
    {
        try
        {
            return getJob(idJob).getMessages();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public int getJobProgress(int idJob)
    {
        try
        {
            return getJob(idJob).getProgress();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Deliverables retrieval
    ///////////////////////////////////////////////////////////////////////

    @Override
    public List<com.enioka.jqm.client.api.Deliverable> getJobDeliverables(int idJob)
    {
        try
        {
            return target.path("ji/" + idJob + "/files").request().get(new GenericType<List<Deliverable>>()
            {
            });
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public List<InputStream> getJobDeliverablesContent(int idJob)
    {
        List<InputStream> res = new ArrayList<>();
        for (Deliverable d : getJobDeliverables(idJob))
        {
            res.add(getDeliverableContent(d));
        }
        return res;
    }

    @Override
    public InputStream getDeliverableContent(com.enioka.jqm.client.api.Deliverable d)
    {
        try
        {
            return target.path("ji/files").request().post(Entity.entity(d, MediaType.APPLICATION_XML), InputStream.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public InputStream getDeliverableContent(int d)
    {
        try
        {
            return target.path("ji/files/" + d).request().get(InputStream.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public InputStream getJobLogStdErr(int jobId)
    {
        try
        {
            return target.path("ji/" + jobId + "/stderr").request().get(InputStream.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public InputStream getJobLogStdOut(int jobId)
    {
        try
        {
            return target.path("ji/" + jobId + "/stdout").request().get(InputStream.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Queue APIs
    ///////////////////////////////////////////////////////////////////////

    @Override
    public List<com.enioka.jqm.client.api.Queue> getQueues()
    {
        try
        {
            return target.path("q").request().get(new GenericType<List<Queue>>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void pauseQueue(Queue q)
    {
        try
        {
            target.path("q/" + q.getId() + "/pause").request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void resumeQueue(Queue q)
    {
        try
        {
            target.path("q/" + q.getId() + "/pause").request().delete();
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public void clearQueue(Queue q)
    {
        try
        {
            target.path("q/" + q.getId() + "/clear").request().post(null);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public QueueStatus getQueueStatus(Queue q)
    {
        try
        {
            return target.path("q/" + q.getId() + "/status").request().get().readEntity(QueueStatus.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public int getQueueEnabledCapacity(Queue q)
    {
        try
        {
            return target.path("q/" + q.getId() + "/enabled-capacity").request().get().readEntity(Integer.class);
        }
        catch (BadRequestException e)
        {
            throw new JqmInvalidRequestException(e.getResponse().readEntity(String.class), e);
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // Parameters retrieval
    ///////////////////////////////////////////////////////////////////////

    @Override
    public List<JobDef> getJobDefinitions()
    {
        try
        {
            return target.path("jd").request().get(new GenericType<List<JobDef>>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public List<JobDef> getJobDefinitions(String application)
    {
        try
        {
            return target.path("jd/" + application).request().get(new GenericType<List<JobDef>>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }

    @Override
    public JobDef getJobDefinition(String name)
    {
        try
        {
            return target.path("jd/name/" + name).request().get(new GenericType<JobDef>()
            {
            });
        }
        catch (Exception e)
        {
            throw new JqmClientException(e);
        }
    }
}
