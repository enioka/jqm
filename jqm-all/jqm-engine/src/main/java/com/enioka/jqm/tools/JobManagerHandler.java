package com.enioka.jqm.tools;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import javax.naming.NamingException;
import javax.naming.spi.NamingManager;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;

import com.enioka.jqm.api.JobRequest;
import com.enioka.jqm.api.JqmClient;
import com.enioka.jqm.api.JqmClientFactory;
import com.enioka.jqm.api.Query;
import com.enioka.jqm.jpamodel.Deliverable;
import com.enioka.jqm.jpamodel.JobDef;
import com.enioka.jqm.jpamodel.JobInstance;
import com.enioka.jqm.jpamodel.JobParameter;
import com.enioka.jqm.jpamodel.Node;
import com.enioka.jqm.jpamodel.State;

/**
 * This is the implementation behind the proxy described in {@link JobManager}.
 */
class JobManagerHandler implements InvocationHandler
{
    private static Logger jqmlogger = Logger.getLogger(JobManagerHandler.class);

    private JobInstance ji;
    private JobDef jd = null;
    private Properties p = null;
    private Map<String, String> params = null;
    private String defaultCon = null, application = null, sessionId = null;
    private Node node = null;

    JobManagerHandler(JobInstance ji)
    {

        p = new Properties();
        p.put("emf", Helpers.getEmf());

        EntityManager em = Helpers.getNewEm();
        this.ji = em.find(JobInstance.class, ji.getId());
        params = new HashMap<String, String>();
        for (JobParameter p : this.ji.getParameters())
        {
            params.put(p.getKey(), p.getValue());
        }

        defaultCon = em.createQuery("SELECT gp.value FROM GlobalParameter gp WHERE gp.key = 'defaultConnection'", String.class)
                .getSingleResult();

        this.jd = this.ji.getJd();
        this.application = this.jd.getApplication();
        this.sessionId = this.ji.getSessionID();
        this.node = this.ji.getNode();
        this.node.getDlRepo();
        em.close();
    }

    private JqmClient getJqmClient()
    {
        return JqmClientFactory.getClient("uncached", p, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        Class<?>[] classes = method.getParameterTypes();
        jqmlogger.trace("An engine API method was called: " + methodName + " with nb arguments: " + classes.length);
        shouldKill();

        if (classes.length == 0)
        {
            if ("jobApplicationId".equals(methodName))
            {
                return jd.getId();
            }
            else if ("parentID".equals(methodName))
            {
                return ji.getParentId();
            }
            else if ("jobInstanceID".equals(methodName))
            {
                return ji.getId();
            }
            else if ("canBeRestarted".equals(methodName))
            {
                return jd.isCanBeRestarted();
            }
            else if ("applicationName".equals(methodName))
            {
                return jd.getApplicationName();
            }
            else if ("sessionID".equals(methodName))
            {
                return ji.getSessionID();
            }
            else if ("application".equals(methodName))
            {
                return application;
            }
            else if ("module".equals(methodName))
            {
                return jd.getModule();
            }
            else if ("keyword1".equals(methodName))
            {
                return jd.getKeyword1();
            }
            else if ("keyword2".equals(methodName))
            {
                return jd.getKeyword2();
            }
            else if ("keyword3".equals(methodName))
            {
                return jd.getKeyword3();
            }
            else if ("userName".equals(methodName))
            {
                return ji.getUserName();
            }
            else if ("parameters".equals(methodName))
            {
                return params;
            }
            else if ("defaultConnect".equals(methodName))
            {
                return this.defaultCon;
            }
            else if ("getDefaultConnection".equals(methodName))
            {
                return this.getDefaultConnection();
            }
            else if ("getWorkDir".equals(methodName))
            {
                return getWorkDir();
            }
            else if ("yield".equals(methodName))
            {
                return null;
            }
            else if ("waitChildren".equals(methodName))
            {
                waitChildren();
                return null;
            }
        }
        else if ("sendMsg".equals(methodName) && classes.length == 1 && classes[0] == String.class)
        {
            sendMsg((String) args[0]);
            return null;
        }
        else if ("sendProgress".equals(methodName) && classes.length == 1 && classes[0] == Integer.class)
        {
            sendProgress((Integer) args[0]);
            return null;
        }
        else if ("enqueue".equals(methodName) && classes.length == 10 && classes[0] == String.class)
        {
            return enqueue((String) args[0], (String) args[1], (String) args[2], (String) args[3], (String) args[4], (String) args[5],
                    (String) args[6], (String) args[7], (String) args[8], (Map<String, String>) args[9]);
        }
        else if ("enqueueSync".equals(methodName) && classes.length == 10 && classes[0] == String.class)
        {
            return enqueueSync((String) args[0], (String) args[1], (String) args[2], (String) args[3], (String) args[4], (String) args[5],
                    (String) args[6], (String) args[7], (String) args[8], (Map<String, String>) args[9]);
        }
        else if ("addDeliverable".equals(methodName) && classes.length == 2 && classes[0] == String.class && classes[1] == String.class)
        {
            return addDeliverable((String) args[0], (String) args[1]);
        }
        else if ("waitChild".equals(methodName) && classes.length == 1 && classes[0] == Integer.class)
        {
            waitChild((Integer) args[0]);
            return null;
        }

        throw new NoSuchMethodException(methodName);
    }

    private void shouldKill()
    {
        EntityManager em = Helpers.getNewEm();
        try
        {
            this.ji = em.find(JobInstance.class, this.ji.getId());
            jqmlogger.trace("Analysis: should JI " + ji.getId() + " get killed? Status is " + ji.getState());
            if (ji.getState().equals(State.KILLED))
            {
                jqmlogger.info("Job will be killed at the request of a user");
                Thread.currentThread().interrupt();
                throw new JqmKillException("This job" + "(ID: " + ji.getId() + ")" + " has been killed by a user");
            }
        }
        finally
        {
            em.close();
        }
    }

    /**
     * Create a {@link com.enioka.jqm.jpamodel.Message} with the given message. The {@link com.enioka.jqm.jpamodel.History} to link to is
     * deduced from the context.
     * 
     * @param msg
     * @throws JqmKillException
     */
    private void sendMsg(String msg)
    {
        EntityManager em = Helpers.getNewEm();
        try
        {
            em.getTransaction().begin();
            Helpers.createMessage(msg, ji, em);
            em.getTransaction().commit();
        }
        finally
        {
            em.close();
        }
    }

    /**
     * Update the {@link com.enioka.jqm.jpamodel.History} with the given progress data.
     * 
     * @param msg
     * @throws JqmKillException
     */
    private void sendProgress(Integer msg)
    {
        EntityManager em = Helpers.getNewEm();
        try
        {
            em.getTransaction().begin();
            this.ji = em.find(JobInstance.class, this.ji.getId(), LockModeType.PESSIMISTIC_WRITE);
            ji.setProgress(msg);
            em.getTransaction().commit();
        }
        finally
        {
            em.close();
        }
    }

    private Integer enqueue(String applicationName, String user, String mail, String sessionId, String application, String module,
            String keyword1, String keyword2, String keyword3, Map<String, String> parameters)
    {
        JobRequest jr = new JobRequest(applicationName, user, mail);
        jr.setApplicationName(applicationName);
        jr.setUser(user == null ? ji.getUserName() : user);
        jr.setEmail(mail);
        jr.setSessionID(sessionId == null ? this.sessionId : sessionId);
        jr.setApplication(application == null ? jd.getApplication() : application);
        jr.setModule(module == null ? jd.getModule() : module);
        jr.setKeyword1(keyword1);
        jr.setKeyword2(keyword2);
        jr.setKeyword3(keyword3);
        jr.setParentID(this.ji.getId());
        jr.setParameters(parameters);

        return getJqmClient().enqueue(jr);
    }

    private int enqueueSync(String applicationName, String user, String mail, String sessionId, String application, String module,
            String keyword1, String keyword2, String keyword3, Map<String, String> parameters)
    {
        int i = enqueue(applicationName, user, mail, sessionId, application, module, keyword1, keyword2, keyword3, parameters);
        waitChild(i);
        return i;
    }

    private void waitChild(int id)
    {
        JqmClient c = getJqmClient();
        Query q = Query.create().setQueryHistoryInstances(false).setQueryLiveInstances(true).setJobInstanceId(id);

        while (c.getJobs(q).size() > 0)
        {
            try
            {
                Thread.sleep(1000);
                shouldKill();
            }
            catch (InterruptedException e)
            {
                break;
            }
        }
    }

    private void waitChildren()
    {
        JqmClient c = getJqmClient();
        Query q = Query.create().setQueryHistoryInstances(false).setQueryLiveInstances(true).setParentId(ji.getId());

        while (c.getJobs(q).size() > 0)
        {
            try
            {
                Thread.sleep(1000);
                shouldKill();
            }
            catch (InterruptedException e)
            {
                break;
            }
        }
    }

    private Integer addDeliverable(String path, String fileLabel) throws IOException
    {
        Deliverable d = null;
        EntityManager em = Helpers.getNewEm();
        try
        {
            this.ji = em.find(JobInstance.class, ji.getId());

            String outputRoot = this.ji.getNode().getDlRepo();
            String ext = FilenameUtils.getExtension(path);
            String destPath = FilenameUtils.concat(outputRoot,
                    "" + ji.getJd().getApplicationName() + "/" + ji.getId() + "/" + UUID.randomUUID() + "." + ext);
            String fileName = FilenameUtils.getName(path);
            FileUtils.moveFile(new File(path), new File(destPath));
            jqmlogger.debug("A deliverable is added. Stored as " + destPath + ". Initial name: " + fileName);

            em.getTransaction().begin();
            d = Helpers.createDeliverable(destPath, fileName, fileLabel, this.ji.getId(), em);
            em.getTransaction().commit();
        }
        finally
        {
            em.close();
        }
        return d.getId();
    }

    private File getWorkDir()
    {
        File f = new File(FilenameUtils.concat(node.getDlRepo(), "" + this.ji.getId()));
        if (!f.isDirectory() && !f.mkdir())
        {
            throw new JqmRuntimeException("Could not create work directory");
        }
        return f;
    }

    private DataSource getDefaultConnection() throws NamingException
    {
        Object dso = NamingManager.getInitialContext(null).lookup(this.defaultCon);
        DataSource q = (DataSource) dso;

        return q;
    }
}
