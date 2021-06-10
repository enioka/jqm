/**
* * Copyright © 2013 enioka. All rights reserved
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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.jdbc.DatabaseUnreachableException;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobInstance;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.ResourceManager;
import com.enioka.jqm.model.State;

/**
 * A thread that polls a queue according to the parameters defined inside a {@link DeploymentParameter}.
 */
class QueuePoller implements Runnable, QueuePollerMBean
{
    private static Logger jqmlogger = LoggerFactory.getLogger(QueuePoller.class);

    private Queue queue = null;
    private JqmEngine engine;
    private int maxNbThread = 10;
    private int pollingInterval = 10000;
    private int dpId;
    private boolean strictPollingPeriod = false;

    private boolean run = true;
    private AtomicInteger actualNbThread = new AtomicInteger(0);
    private boolean hasStopped = true;
    private Calendar lastLoop = null;
    private Map<Integer, Date> peremption = new ConcurrentHashMap<>();

    private List<ResourceManagerBase> resourceManagers = new ArrayList<>();
    private ResourceManager threadresourceManagerConfiguration;

    private ObjectName name = null;

    private Thread localThread = null;
    private Semaphore loop;

    @Override
    public void stop()
    {
        jqmlogger.info("Poller " + queue.getName() + " has received a stop order");
        run = false;
        loop.release();
    }

    /**
     * Will make the thread ready to run once again after it has stopped.
     */
    void reset()
    {
        if (!hasStopped)
        {
            throw new IllegalStateException("cannot reset a non stopped queue poller");
        }
        hasStopped = false;
        run = true;
        lastLoop = null;
        loop = new Semaphore(0);
    }

    QueuePoller(JqmEngine engine, Queue q, DeploymentParameter dp)
    {
        this.engine = engine;
        this.queue = q;

        // Hard code a thread count RM and link it to the DP parameters (transitory - no db change for now this way)
        this.threadresourceManagerConfiguration = new ResourceManager();
        threadresourceManagerConfiguration.setClassName(QuantityResourceManager.class.getCanonicalName());
        threadresourceManagerConfiguration.setDeploymentParameterId(dp.getId());
        threadresourceManagerConfiguration.setEnabled(true);
        threadresourceManagerConfiguration.setKey("thread");
        threadresourceManagerConfiguration.setNodeId(null);
        threadresourceManagerConfiguration.addParameter("com.enioka.jqm.rm.quantity.quantity", "" + dp.getNbThread());
        QuantityResourceManager threadResourceManager = new QuantityResourceManager(threadresourceManagerConfiguration);
        this.resourceManagers.add(threadResourceManager);

        // Hard code a highlander RM
        ResourceManager highlanderResourceManagerConfiguration = new ResourceManager();
        highlanderResourceManagerConfiguration.setClassName(HighlanderResourceManager.class.getCanonicalName());
        highlanderResourceManagerConfiguration.setDeploymentParameterId(dp.getId());
        highlanderResourceManagerConfiguration.setEnabled(true);
        highlanderResourceManagerConfiguration.setKey("highlander");
        highlanderResourceManagerConfiguration.setNodeId(null);
        HighlanderResourceManager highlanderResourceManager = new HighlanderResourceManager(highlanderResourceManagerConfiguration);
        this.resourceManagers.add(highlanderResourceManager);

        // Add global resource managers
        this.resourceManagers.addAll(engine.getResourceManagers());

        // Synchronize parameters
        applyDeploymentParameter(dp);

        reset();
        registerMBean();
    }

    void applyDeploymentParameter(DeploymentParameter dp)
    {
        this.pollingInterval = dp.getPollingInterval();
        this.maxNbThread = dp.getEnabled() ? dp.getNbThread() : 0;
        this.dpId = dp.getId();

        jqmlogger.info("Engine {}" + " will poll JobInstances on queue {} every {} s", engine.getNode().getName(), queue.getName(),
                pollingInterval / 1000);

        this.threadresourceManagerConfiguration.addParameter("com.enioka.jqm.rm.quantity.quantity", "" + this.maxNbThread);
        this.resourceManagers.get(0).refreshConfiguration(this.threadresourceManagerConfiguration);
    }

    /**
     * Called at the beginning of the main loop to check if the poller config is up to date (nbThread, pause...)
     */
    private void refreshDeploymentParameter(DbConn cnx)
    {
        List<DeploymentParameter> prms = DeploymentParameter.select(cnx, "dp_select_by_id", this.dpId);
        if (prms.size() != 1)
        {
            this.stop();
            return;
        }

        DeploymentParameter p = prms.get(0);
        if (p.getPollingInterval() != this.pollingInterval || (p.getEnabled() && this.maxNbThread != p.getNbThread())
                || (this.maxNbThread > 0 && !p.getEnabled()) || (this.maxNbThread == 0 && p.getEnabled()))
        {
            applyDeploymentParameter(p);
        }

        this.strictPollingPeriod = Boolean.parseBoolean(GlobalParameter.getParameter(cnx, "strictPollingPeriod", "false"));
    }

    private void registerMBean()
    {
        try
        {
            if (this.engine.loadJmxBeans)
            {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                name = new ObjectName(
                        "com.enioka.jqm:type=Node.Queue,Node=" + this.engine.getNode().getName() + ",name=" + this.queue.getName());

                // Unregister MBean if it already exists. This may happen during frequent DP modifications.
                try
                {
                    mbs.getMBeanInfo(name);
                    mbs.unregisterMBean(name);
                }
                catch (InstanceNotFoundException e)
                {
                    // Nothing to do, this should be the normal case.
                }

                mbs.registerMBean(this, name);
            }
        }
        catch (Exception e)
        {
            throw new JqmInitError("Could not create JMX beans", e);
        }
    }

    private int potentialFreeRoom()
    {
        int room = Integer.MAX_VALUE;
        for (ResourceManagerBase rm : this.resourceManagers)
        {
            room = Math.min(room, rm.getSlotsAvailable());
        }
        return room;
    }

    @Override
    public synchronized void run() // sync: avoid race condition on run when restarting after failure.
    {
        this.localThread = Thread.currentThread();
        this.localThread.setName("QUEUE_POLLER;polling;" + this.queue.getName());
        DbConn cnx = null;

        while (true)
        {
            lastLoop = Calendar.getInstance();
            jqmlogger.trace("poller loop");

            try
            {
                // Always check latest polling parameters
                cnx = Helpers.getNewDbSession();
                refreshDeploymentParameter(cnx);

                // Free room?
                int freeRoom = potentialFreeRoom();
                if (freeRoom > 0)
                {
                    // Fetch the queue head. * 3 because we may reject quite a few JI inside resource managers.
                    List<JobInstance> newInstances = cnx.poll(this.queue, freeRoom > 100000 ? Integer.MAX_VALUE : freeRoom * 3);

                    jiloop: for (JobInstance ji : newInstances)
                    {
                        // TODO: bulk load parameters
                        ji.loadPrmCache(cnx);

                        // Check if we have the resources needed to run this JI
                        List<ResourceManagerBase> alreadyReserved = new ArrayList<>(this.resourceManagers.size());
                        for (ResourceManagerBase rm : this.resourceManagers)
                        {
                            switch (rm.bookResource(ji, cnx))
                            {
                            case BOOKED:
                                // OK, nothing to do.
                                alreadyReserved.add(rm);
                                break;
                            case EXHAUSTED:
                                // Stop the loop - cannot do anything anymore with these resources.
                                jqmlogger.trace("Poller has a full RM");
                                for (ResourceManagerBase reservedRm : alreadyReserved)
                                {
                                    reservedRm.releaseResource(ji);
                                }
                                break jiloop;
                            case FAILED:
                                // Skip this JI - no resource for it but there may be resources for the next ones.
                                jqmlogger.trace("Head JI asks for unavailable resources, skipping to next one");
                                for (ResourceManagerBase reservedRm : alreadyReserved)
                                {
                                    reservedRm.releaseResource(ji);
                                }
                                continue jiloop;
                            }
                        }

                        actualNbThread.incrementAndGet();

                        // Actually set it for running on this node and report it on the in-memory object.
                        QueryResult qr = cnx.runUpdate("ji_update_status_by_id", this.engine.getNode().getId(), ji.getId());
                        if (qr.nbUpdated != 1)
                        {
                            // Means the JI was taken by another node, so simply continue.
                            releaseResources(ji);
                            continue;
                        }
                        ji.setNode(this.engine.getNode());
                        ji.setState(State.ATTRIBUTED);

                        // Commit taking possession of the JI (as well as anything whih may have been done inside the RMs)
                        cnx.commit();

                        // We will run this JI!
                        jqmlogger.trace("JI number {} will be run by this poller this loop (already {}/{} on {})", ji.getId(),
                                actualNbThread, maxNbThread, this.queue.getName());
                        if (ji.getJD().getMaxTimeRunning() != null)
                        {
                            this.peremption.put(ji.getId(), new Date((new Date()).getTime() + ji.getJD().getMaxTimeRunning() * 60 * 1000));
                        }

                        // Run it
                        if (!ji.getJD().isExternal())
                        {
                            this.engine.getRunningJobInstanceManager().startNewJobInstance(ji, this);
                        }
                        else
                        {
                            (new Thread(new RunningExternalJobInstance(cnx, ji, this))).start();
                        }
                    }
                }
            }
            catch (DatabaseUnreachableException e)
            {
                if (!run)
                {
                    break;
                }
                jqmlogger.error("connection to database lost - stopping poller");
                jqmlogger.trace("connection error was:", e.getCause());
                this.hasStopped = true;
                this.engine.pollerRestartNeeded(this);
                break;
            }
            catch (RuntimeException e)
            {
                if (!run)
                {
                    break;
                }
                jqmlogger.error("Queue poller has failed! It will stop.", e);
                this.run = false;
                this.hasStopped = true;
                break;
            }
            catch (Exception e)
            {
                jqmlogger.error("Queue poller has failed! It will stop.", e);
                this.run = false;
                this.hasStopped = true;
                break;
            }
            finally
            {
                // Reset the connection on each loop.
                if (Thread.interrupted()) // always clear interrupted status before doing DB operations.
                {
                    run = false;
                }
                Helpers.closeQuietly(cnx);
            }

            // Wait according to the deploymentParameter
            try
            {
                loop.tryAcquire(this.pollingInterval, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                run = false;
                break;
            }

            // Exit if asked to
            if (!run)
            {
                break;
            }
        }

        if (!run)
        {
            // Run is true only if the loop has exited abnormally, in which case the engine should try to restart the poller
            // So only do the graceful shutdown procedure if normal shutdown.

            jqmlogger
                    .info("Poller loop on queue " + this.queue.getName() + " is stopping [engine " + this.engine.getNode().getName() + "]");
            waitForAllThreads(60L * 1000);

            // JMX
            if (this.engine.loadJmxBeans)
            {
                try
                {
                    ManagementFactory.getPlatformMBeanServer().unregisterMBean(name);
                }
                catch (Exception e)
                {
                    jqmlogger.error("Could not unregister JMX beans", e);
                }
            }

            // Let the engine decide if it should stop completely
            this.hasStopped = true; // BEFORE check
            jqmlogger.info("Poller on queue " + this.queue.getName() + " has ended normally");
            this.engine.checkEngineEnd();
        }
        else
        {
            // else => Abnormal stop (DB failure only). Set booleans to reflect this.
            jqmlogger.error("Poller on queue " + this.queue.getName() + " has ended abnormally");
            this.run = false;
            this.hasStopped = true;
            // Do not check for engine end - we do not want to shut down the engine on a poller failure.
        }
        localThread = null;
    }

    @Override
    public Integer getCurrentActiveThreadCount()
    {
        return actualNbThread.get();
    }

    /**
     * Called when a payload thread has ended. This also notifies the poller to poll once again.
     */
    void releaseResources(JobInstance ji)
    {
        this.peremption.remove(ji.getId());
        this.actualNbThread.decrementAndGet();

        for (ResourceManagerBase rm : this.resourceManagers)
        {
            rm.releaseResource(ji);
        }

        if (!this.strictPollingPeriod)
        {
            // Force a new loop at once. This makes queues more fluid.
            loop.release(1);
        }
        this.engine.signalEndOfRun();
    }

    boolean isRunning()
    {
        return !this.hasStopped;
    }

    private void waitForAllThreads(long timeOutMs)
    {
        long timeWaitedMs = 0;
        long stepMs = 1000;
        while (timeWaitedMs <= timeOutMs)
        {
            jqmlogger.trace("Waiting the end of {} job(s)", actualNbThread);

            if (actualNbThread.get() == 0)
            {
                break;
            }
            if (timeWaitedMs == 0)
            {
                jqmlogger.info("Waiting for the end of {} jobs on queue {} - timeout is {} ms", actualNbThread, this.queue.getName(),
                        timeOutMs);
            }
            try
            {
                Thread.sleep(stepMs);
            }
            catch (InterruptedException e)
            {
                // Interruption => stop right now
                jqmlogger.warn("Some job instances did not finish in time - wait was interrupted");
                Thread.currentThread().interrupt();
                return;
            }
            timeWaitedMs += stepMs;
        }
        if (timeWaitedMs > timeOutMs)
        {
            jqmlogger.warn("Some job instances did not finish in time - they will be killed for the poller to be able to stop");
        }
    }

    Queue getQueue()
    {
        return this.queue;
    }

    JqmEngine getEngine()
    {
        return this.engine;
    }

    void setMaxThreads(int max)
    {
        if (this.maxNbThread > 0 && max == 0)
        {
            jqmlogger.info("Poller is being paused - it won't fetch any new job instances until it is resumed.");
        }
        else if (this.maxNbThread == 0 && max > 0)
        {
            jqmlogger.info("Poller is being resumed");
        }
        this.maxNbThread = max;
    }

    void setPollingInterval(int ms)
    {
        this.pollingInterval = ms;
    }

    // //////////////////////////////////////////////////////////
    // JMX
    // //////////////////////////////////////////////////////////

    @Override
    public long getCumulativeJobInstancesCount()
    {
        try (DbConn em2 = Helpers.getNewDbSession())
        {
            return em2.runSelectSingle("history_select_count_for_poller", Long.class, this.queue.getId(), this.engine.getNode().getId());
        }
    }

    @Override
    public float getJobsFinishedPerSecondLastMinute()
    {
        try (DbConn em2 = Helpers.getNewDbSession())
        {
            return em2.runSelectSingle("history_select_count_last_mn_for_poller", Float.class, this.queue.getId(),
                    this.engine.getNode().getId());
        }
    }

    @Override
    public long getCurrentlyRunningJobCount()
    {
        return this.actualNbThread.get();
    }

    @Override
    public Integer getPollingIntervalMilliseconds()
    {
        return this.pollingInterval;
    }

    @Override
    public Integer getMaxConcurrentJobInstanceCount()
    {
        return this.maxNbThread;
    }

    @Override
    public boolean isActuallyPolling()
    {
        // 1000ms is a rough estimate of the time taken to do the actual poll. If it's more, there is a huge issue elsewhere.
        return this.lastLoop != null
                && (Calendar.getInstance().getTimeInMillis() - this.lastLoop.getTimeInMillis()) <= pollingInterval + 1000;
    }

    @Override
    public boolean isFull()
    {
        return this.actualNbThread.get() >= maxNbThread;
    }

    @Override
    public int getLateJobs()
    {
        int i = 0;
        Date now = new Date();
        for (Date d : this.peremption.values())
        {
            if (now.after(d))
            {
                i++;
            }
        }
        return i;
    }
}
