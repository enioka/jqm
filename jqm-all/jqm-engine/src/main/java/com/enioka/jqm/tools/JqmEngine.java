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

import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Semaphore;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;
import org.eclipse.jetty.util.ArrayQueue;

import com.enioka.jqm.jpamodel.DeploymentParameter;
import com.enioka.jqm.jpamodel.GlobalParameter;
import com.enioka.jqm.jpamodel.History;
import com.enioka.jqm.jpamodel.JobInstance;
import com.enioka.jqm.jpamodel.Message;
import com.enioka.jqm.jpamodel.Node;
import com.enioka.jqm.jpamodel.State;

/**
 * The engine itself. Everything starts in this class.
 */
class JqmEngine implements JqmEngineMBean
{
    private static Logger jqmlogger = Logger.getLogger(JqmEngine.class);
    static String latestNodeStartedName = "";

    // Sync data for stopping the engine
    private Semaphore ended = new Semaphore(0);
    private boolean hasEnded = false;

    // Parameters and parameter cache
    private Node node = null;
    private LibraryCache cache = new LibraryCache();
    private ObjectName name;

    // Threads that together constitute the engine
    private Map<Integer, QueuePoller> pollers = new HashMap<Integer, QueuePoller>();
    private InternalPoller intPoller = null;
    private JettyServer server = null;

    // Misc data
    private Calendar startTime = Calendar.getInstance();
    private Thread killHook = null;
    boolean loadJmxBeans = true;

    // DB connection resilience data
    private volatile Queue<QueuePoller> qpToRestart = new ArrayQueue<QueuePoller>();
    private volatile Queue<Loader> loaderToFinalize = new ArrayQueue<Loader>();
    private volatile Thread qpRestarter = null;

    /**
     * Starts the engine
     * 
     * @param nodeName
     *            the name of the node to start, as in the NODE table of the database.
     * @throws JqmInitError
     */
    void start(String nodeName)
    {
        if (nodeName == null || nodeName.isEmpty())
        {
            throw new IllegalArgumentException("nodeName cannot be null or empty");
        }

        // Set thread name - used in audits
        Thread.currentThread().setName("JQM engine;;" + nodeName);
        Helpers.setLogFileName(nodeName);

        // Log: we are starting...
        jqmlogger.info("JQM engine for node " + nodeName + " is starting");

        // JNDI first - the engine itself uses JNDI to fetch its connections!
        Helpers.registerJndiIfNeeded();

        // Database connection
        EntityManager em = Helpers.getNewEm();

        // Node configuration is in the database
        Helpers.checkConfiguration(nodeName, em);
        node = em.createQuery("SELECT n FROM Node n WHERE n.name = :l", Node.class).setParameter("l", nodeName).getSingleResult();

        // Log parameters
        Helpers.dumpParameters(em, node);

        // Check if double-start
        long toWait = (long) (2 * Long.parseLong(Helpers.getParameter("aliveSignalMs", "60000", em)));
        if (node.getLastSeenAlive() != null
                && Calendar.getInstance().getTimeInMillis() - node.getLastSeenAlive().getTimeInMillis() <= toWait)
        {
            long r = Calendar.getInstance().getTimeInMillis() - node.getLastSeenAlive().getTimeInMillis();
            throw new JqmInitErrorTooSoon("Another engine named " + nodeName + " was running no less than " + r / 1000
                    + " seconds ago. Either stop the other node, or if it already stopped, please wait " + (toWait - r) / 1000 + " seconds");
        }

        // Prevent very quick multiple starts by immediately setting the keep-alive
        em.getTransaction().begin();
        node.setLastSeenAlive(Calendar.getInstance());
        em.getTransaction().commit();

        // Log level
        Helpers.setLogLevel(node.getRootLogLevel());

        // Log multicasting (& log4j stdout redirect)
        GlobalParameter gp1 = em.createQuery("SELECT g FROM GlobalParameter g WHERE g.key = :k", GlobalParameter.class)
                .setParameter("k", "logFilePerLaunch").getSingleResult();
        if ("true".equals(gp1.getValue()))
        {
            RollingFileAppender a = (RollingFileAppender) Logger.getRootLogger().getAppender("rollingfile");
            MulticastPrintStream s = new MulticastPrintStream(System.out, FilenameUtils.getFullPath(a.getFile()));
            System.setOut(s);
            ((ConsoleAppender) Logger.getRootLogger().getAppender("consoleAppender")).setWriter(new OutputStreamWriter(s));
            s = new MulticastPrintStream(System.err, FilenameUtils.getFullPath(a.getFile()));
            System.setErr(s);
        }

        // Remote JMX server
        if (node.getJmxRegistryPort() != null && node.getJmxServerPort() != null && node.getJmxRegistryPort() > 0
                && node.getJmxServerPort() > 0)
        {
            JmxAgent.registerAgent(node.getJmxRegistryPort(), node.getJmxServerPort(), node.getDns());
        }
        else
        {
            jqmlogger
                    .info("JMX remote listener will not be started as JMX registry port and JMX server port parameters are not both defined");
        }

        // Jetty
        this.server = new JettyServer();
        this.server.start(node, em);

        // JMX
        if (node.getJmxServerPort() != null && node.getJmxServerPort() > 0)
        {
            try
            {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                name = new ObjectName("com.enioka.jqm:type=Node,name=" + this.node.getName());
                mbs.registerMBean(this, name);
            }
            catch (Exception e)
            {
                throw new JqmInitError("Could not create JMX beans", e);
            }
            jqmlogger.info("JMX management bean for the engine was registered");
        }
        else
        {
            loadJmxBeans = false;
            jqmlogger.info("JMX management beans will not be loaded as JMX server port is null or zero");
        }

        // Security
        if (System.getSecurityManager() == null)
        {
            System.setSecurityManager(new SecurityManagerPayload());
        }
        jqmlogger.info("Security manager was registered");

        // Cleanup
        purgeDeadJobInstances(em, this.node);

        // Pollers
        syncPollers(em, this.node);
        jqmlogger.info("All required queues are now polled");

        // Internal poller (stop notifications, keepalive)
        intPoller = new InternalPoller(this);
        Thread t = new Thread(intPoller);
        t.start();

        // Kill notifications
        killHook = new SignalHandler(this);
        Runtime.getRuntime().addShutdownHook(killHook);

        // Done
        em.close();
        em = null;
        latestNodeStartedName = node.getName();
        jqmlogger.info("End of JQM engine initialization");
    }

    /**
     * Gracefully stop the engine
     */
    @Override
    public void stop()
    {
        synchronized (killHook)
        {
            jqmlogger.info("JQM engine " + this.node.getName() + " has received a stop order");

            // Kill hook should be removed
            try
            {
                if (!Runtime.getRuntime().removeShutdownHook(killHook))
                {
                    jqmlogger.error("The engine could not unregister its shutdown hook");
                }
            }
            catch (IllegalStateException e)
            {
                // This happens if the stop sequence is initiated by the shutdown hook itself.
                jqmlogger.info("Stop order is due to an admin operation (KILL/INT)");
            }
        }

        // Stop pollers
        for (QueuePoller p : pollers.values())
        {
            p.stop();
        }

        // Jetty is closed automatically when all pollers are down

        // Wait for the end of the world
        try
        {
            this.ended.acquire();
        }
        catch (InterruptedException e)
        {
            jqmlogger.error("interrupted", e);
        }
        jqmlogger.debug("Stop order was correctly handled. Engine for node " + this.node.getName() + " has stopped.");
    }

    Node getNode()
    {
        return this.node;
    }

    void syncPollers(EntityManager em, Node node)
    {
        List<DeploymentParameter> dps = em
                .createQuery("SELECT dp FROM DeploymentParameter dp WHERE dp.node.id = :n", DeploymentParameter.class)
                .setParameter("n", node.getId()).getResultList();

        QueuePoller p = null;
        for (DeploymentParameter i : dps)
        {
            if (pollers.containsKey(i.getId()))
            {
                p = pollers.get(i.getId());
                p.setMaxThreads(i.getNbThread());
                p.setPollingInterval(i.getPollingInterval());
            }
            else
            {
                p = new QueuePoller(this, i.getQueue(), i.getNbThread(), i.getPollingInterval());
                pollers.put(i.getId(), p);
                Thread t = new Thread(p);
                t.start();
            }
        }

        // Remove deleted pollers
        for (int dp : this.pollers.keySet().toArray(new Integer[0]))
        {
            boolean found = false;
            for (DeploymentParameter ndp : dps)
            {
                if (ndp.getId().equals(dp))
                {
                    found = true;
                    break;
                }
            }
            if (!found)
            {
                QueuePoller qp = this.pollers.get(dp);
                qp.stop();
                this.pollers.remove(dp);
            }
        }
    }

    synchronized void checkEngineEnd()
    {
        jqmlogger.trace("Checking if engine should end with the latest poller");
        for (QueuePoller poller : pollers.values())
        {
            if (poller.isRunning())
            {
                jqmlogger.trace("At least the poller on queue " + poller.getQueue().getName() + " is still running and prevents shutdown");
                return;
            }
        }
        if (hasEnded)
        {
            return;
        }
        jqmlogger.trace("The engine should end with the latest poller");
        hasEnded = true;

        // If here, all pollers are down. Stop Jetty too
        this.server.stop();

        // Also stop the internal poller
        this.intPoller.stop();

        // Reset the stop counter - we may want to restart one day
        EntityManager em = null;
        try
        {
            em = Helpers.getNewEm();
            em.getTransaction().begin();
            this.node = em.find(Node.class, this.node.getId(), LockModeType.PESSIMISTIC_WRITE);
            this.node.setStop(false);
            this.node.setLastSeenAlive(null);
            em.getTransaction().commit();
        }
        catch (Exception e)
        {
            // Shutdown exception is ignored (happens during tests)
        }
        finally
        {
            Helpers.closeQuietly(em);
        }

        // JMX
        if (loadJmxBeans)
        {
            try
            {
                MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
                mbs.unregisterMBean(name);
                jqmlogger.trace("unregistered bean " + name);
            }
            catch (Exception e)
            {
                jqmlogger.error("Could not unregister engine JMX bean", e);
            }
        }

        // Note: if present, the JMX listener is not stopped as it is JVM-global, like the JNDI context

        // Done
        this.ended.release();
        jqmlogger.info("JQM engine has stopped");
    }

    private void purgeDeadJobInstances(EntityManager em, Node node)
    {
        em.getTransaction().begin();
        for (JobInstance ji : em.createQuery("SELECT ji FROM JobInstance ji WHERE ji.node = :node", JobInstance.class)
                .setParameter("node", node).getResultList())
        {
            History h = em.find(History.class, ji.getId());
            if (h == null)
            {
                h = Helpers.createHistory(ji, em, State.CRASHED, Calendar.getInstance());
                Message m = new Message();
                m.setJi(ji.getId());
                m.setTextMessage("Job was supposed to be running at server startup - usually means it was killed along a server by an admin or a crash");
                em.persist(m);
            }

            em.createQuery("DELETE FROM JobInstance WHERE id = :i").setParameter("i", ji.getId()).executeUpdate();
        }
        em.getTransaction().commit();
    }

    /**
     * A poller should call this method when it encounters a database connection issue, and then should stop.<br>
     * This will ensure the poller is restarted when database connectivity is restored.<br>
     * Only pollers which have called this method are restarted, other are deemed not to have crashed.
     */
    void pollerRestartNeeded(QueuePoller qp)
    {
        qpToRestart.add(qp);
        startDbRestarter();
    }

    void loaderFinalizationNeeded(Loader l)
    {
        loaderToFinalize.add(l);
        startDbRestarter();
    }

    synchronized void startDbRestarter()
    {
        // On first alert, start the thread which will check connection restoration and relaunch the pollers.
        if (qpRestarter != null)
        {
            return;
        }

        final JqmEngine ee = this;
        qpRestarter = new Thread()
        {
            @Override
            public void run()
            {
                // Test if the DB is back and wait for it if not
                jqmlogger.warn("The engine will now indefinitely try to restore connection to the database");
                EntityManager em = null;
                boolean back = false;
                int timeToWait = 1;
                while (!back)
                {
                    try
                    {
                        em = Helpers.getNewEm();
                        em.find(Node.class, 1);
                        back = true;
                        jqmlogger.warn("connection to database was restored");
                    }
                    catch (Exception e)
                    {
                        // The db is not back yet
                        try
                        {
                            jqmlogger.debug("waiting for db...");
                            Thread.sleep(1000 * timeToWait);
                            timeToWait = Math.min(timeToWait + 1, 120);
                        }
                        catch (InterruptedException e1)
                        {
                            // Not an issue here.
                        }
                    }
                    finally
                    {
                        Helpers.closeQuietly(em);
                    }
                }

                // Restart pollers
                QueuePoller qp = qpToRestart.poll();
                while (qp != null)
                {
                    jqmlogger.warn("resetting poller on queue " + qp.getQueue().getName());
                    qp.reset();
                    Thread t = new Thread(qp);
                    t.start();
                    qp = qpToRestart.poll();
                }

                // Always restart internal poller
                intPoller.stop();
                ee.intPoller = new InternalPoller(ee);
                Thread t = new Thread(ee.intPoller);
                t.start();

                // Finalize loaders that could not store their result inside the database
                Loader l = loaderToFinalize.poll();
                while (l != null)
                {
                    jqmlogger.warn("storing delayed results for loader " + l.getId());
                    l.endOfRunDb();
                    l = loaderToFinalize.poll();
                }

                // Done - reset the relauncher itself and let the thread end.
                ee.qpRestarter = null;
            }
        };
        qpRestarter.start();
    }

    LibraryCache getCache()
    {
        return this.cache;
    }

    JettyServer getJetty()
    {
        return this.server;
    }

    // //////////////////////////////////////////////////////////////////////////
    // JMX stat methods (they get their own EM to be thread safe)
    // //////////////////////////////////////////////////////////////////////////

    @Override
    public long getCumulativeJobInstancesCount()
    {
        EntityManager em = Helpers.getNewEm();
        Long nb = em.createQuery("SELECT COUNT(i) From History i WHERE i.node = :n", Long.class).setParameter("n", this.node)
                .getSingleResult();
        em.close();
        return nb;
    }

    @Override
    public float getJobsFinishedPerSecondLastMinute()
    {
        EntityManager em = Helpers.getNewEm();
        Calendar minusOneMinute = Calendar.getInstance();
        minusOneMinute.add(Calendar.MINUTE, -1);
        Float nb = em.createQuery("SELECT COUNT(i) From History i WHERE i.endDate >= :d and i.node = :n", Long.class)
                .setParameter("d", minusOneMinute).setParameter("n", this.node).getSingleResult() / 60f;
        em.close();
        return nb;
    }

    @Override
    public long getCurrentlyRunningJobCount()
    {
        EntityManager em = Helpers.getNewEm();
        Long nb = em.createQuery("SELECT COUNT(i) From JobInstance i WHERE i.node = :n", Long.class).setParameter("n", this.node)
                .getSingleResult();
        em.close();
        return nb;
    }

    @Override
    public boolean isAllPollersPolling()
    {
        for (QueuePoller p : this.pollers.values())
        {
            if (!p.isActuallyPolling())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isFull()
    {
        for (QueuePoller p : this.pollers.values())
        {
            if (p.isFull())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public long getUptime()
    {
        return (Calendar.getInstance().getTimeInMillis() - this.startTime.getTimeInMillis()) / 1000;
    }

    @Override
    public String getVersion()
    {
        return Helpers.getMavenVersion();
    }
}
