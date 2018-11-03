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

package com.enioka.jqm.model;

import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.jdbc.QueryResult;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 * Persistence class for storing the definition of all the user codes (the payloads) that can be run by the JQM engines. It contains all the
 * metadata needed to create an execution request (a {@link JobInstance}).
 */
public class JobDef implements Serializable
{
    private static final long serialVersionUID = -3276834475433922990L;

    public enum PathType {
        /**
         * The path is a local file system path.
         */
        FS,
        /**
         * The path is made of a single Maven coordinates set.
         */
        MAVEN,
        /**
         * The payload is actually already in memory - the path is meaningless.
         */
        MEMORY,
        /**
         * The path is a shell command, using the platform-dependent default shell.
         */
        DEFAULTSHELLCOMMAND,
        /**
         * The path is a powershell command
         */
        POWERSHELLCOMMAND,
        /**
         * The path is an executable which does not need a shell to run.
         */
        DIRECTEXECUTABLE,
    }

    private Integer id = null;

    private String applicationName;
    private String description;
    private boolean enabled = true;

    private String javaClassName;

    private PathType pathType;
    private String jarPath;

    private int queue_id;
    private Integer priority;

    private boolean canBeRestarted = true;
    private Integer maxTimeRunning;

    private String application;
    private String module;
    private String keyword1;
    private String keyword2;
    private String keyword3;

    private boolean highlander = false;

    private boolean external = false;
    private String javaOpts;

    private List<JobDefParameter> parameters = new ArrayList<JobDefParameter>();

    private Integer classLoader;

    /**
     * A technical ID without any meaning. Generated by the database.
     */
    public Integer getId()
    {
        return id;
    }

    /**
     * True if instances of this {@link JobDef} can be restarted (i.e. run with exactly the same parameters and context). Default is true.
     */
    public boolean isCanBeRestarted()
    {
        return canBeRestarted;
    }

    /**
     * See {@link #isCanBeRestarted()}
     */
    public void setCanBeRestarted(final boolean canBeRestarted)
    {
        this.canBeRestarted = canBeRestarted;
    }

    /**
     * The "main" class of the payload. I.e. the class containing a static main function, or implementing {@link Runnable}.<br>
     * Must be a fully qualified name.<br>
     * Max length is 100.
     */
    public String getJavaClassName()
    {
        return javaClassName;
    }

    /**
     * See {@link #getJavaClassName()}
     */
    public void setJavaClassName(final String javaClassName)
    {
        this.javaClassName = javaClassName;
    }

    /**
     * An optional hint giving the run time after which an alert should be raised. In minutes.
     */
    public Integer getMaxTimeRunning()
    {
        return maxTimeRunning;
    }

    /**
     * See {@link #getMaxTimeRunning()}
     */
    public void setMaxTimeRunning(final Integer maxTimeRunning)
    {
        this.maxTimeRunning = maxTimeRunning;
    }

    /**
     * The applicative key of the {@link JobDef}. {@link JobDef} are always retrieved through this name.<br>
     * Max length is 100.
     */
    public String getApplicationName()
    {
        return applicationName;
    }

    /**
     * See {@link #getApplicationName()}
     */
    public void setApplicationName(final String applicationName)
    {
        this.applicationName = applicationName;
    }

    /**
     * An optional classification tag (default is NULL).<br>
     * Max length is 50.
     */
    public String getApplication()
    {
        return application;
    }

    /**
     * See {@link #getApplication()}
     */
    public void setApplication(final String application)
    {
        this.application = application;
    }

    /**
     * An optional classification tag (default is NULL).<br>
     * Max length is 50.
     */
    public String getModule()
    {
        return module;
    }

    /**
     * See {@link #getModule()}
     */
    public void setModule(final String module)
    {
        this.module = module;
    }

    /**
     * An optional classification tag (default is NULL).<br>
     * Max length is 50.
     */
    public String getKeyword1()
    {
        return keyword1;
    }

    /**
     * See {@link #getKeyword1()}
     */
    public void setKeyword1(final String keyword1)
    {
        this.keyword1 = keyword1;
    }

    /**
     * An optional classification tag (default is NULL).<br>
     * Max length is 50.
     */
    public String getKeyword2()
    {
        return keyword2;
    }

    /**
     * See {@link #getKeyword2()}
     */
    public void setKeyword2(final String keyword2)
    {
        this.keyword2 = keyword2;
    }

    /**
     * An optional classification tag (default is NULL).<br>
     * Max length is 50.
     */
    public String getKeyword3()
    {
        return keyword3;
    }

    /**
     * See {@link #getKeyword3()}
     */
    public void setKeyword3(final String keyword3)
    {
        this.keyword3 = keyword3;
    }

    /**
     * Set to true to enable Highlander mode: never more than one concurrent execution of the same {@link JobDef} inside the whole cluster.
     * Default is false.
     */
    public boolean isHighlander()
    {
        return highlander;
    }

    /**
     * See {@link #isHighlander()}
     */
    public void setHighlander(final boolean highlander)
    {
        this.highlander = highlander;
    }

    /**
     * The {@link Queue} on which the instances created from this {@link JobDef} should run. This is only the "default" queue - it may be
     * overloaded inside the execution request.
     */
    public Integer getQueue()
    {
        return queue_id;
    }

    public Queue getQueue(DbConn cnx)
    {
        List<Queue> qq = Queue.select(cnx, "q_select_by_id", this.queue_id);
        if (qq.size() == 0)
        {
            throw new NoResultException("No queue found");
        }
        return qq.get(0);
    }

    /**
     * See {@link #getQueue()}
     */
    public void setQueue(final int queue)
    {
        this.queue_id = queue;
    }

    /**
     * The path of the jar file containing the payload to run. The path must be relative to the job repository root ({@link Node#getRepo()}
     * ).<br>
     * Max length is 1024.
     */
    public String getJarPath()
    {
        return jarPath;
    }

    /**
     * See {@link #getJarPath()}
     */
    public void setJarPath(final String jarPath)
    {
        this.jarPath = jarPath;
    }

    /**
     * Parameters (i.e. key/value pairs) that should be present for all instances created from this JobDef. This list may be empty.<br>
     * These are only the "default" parameters - each parameter may be overloaded inside the execution request (which may even specify
     * parameters which are not present in the default parameters).
     */
    public List<JobDefParameter> getParameters(DbConn cnx)
    {
        return JobDefParameter.select(cnx, "jdprm_select_all_for_jd", this.id);
    }

    public Map<String, String> getParametersMap(DbConn cnx)
    {
        return JobDefParameter.select_map(cnx, "jdprm_select_all_for_jd", this.id);
    }

    /**
     * A (compulsory) description of what this paylod does.<br>
     * Max length is 1024.
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * See {@link #getDescription()}
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * The options passed to the JVM when launching this job definition. Only used if {@link #isExternal()} is <code>true</code>.<br>
     * These options are split on spaces and passed individually to the JVM.<br>
     * If <code>null</code>, the global parameter <code>defaultExternalOpts</code> is used. It this parameter is null too, default values
     * are used.
     */
    public String getJavaOpts()
    {
        return javaOpts;
    }

    /**
     * See {@link #getJavaOpts()}
     */
    public void setJavaOpts(String javaOpts)
    {
        this.javaOpts = javaOpts;
    }

    private Cl clCache = null;

    /**
     * Details on the class loader to use to run instances created from this JobDef. A JobDef may have its own class loader or share it with
     * other JobDef.
     */
    public Cl getClassLoader()
    {
        return clCache;
    }

    public Cl getClassLoader(DbConn cnx)
    {
        List<Cl> cls = Cl.select(cnx, "cl_select_by_id", this.classLoader);
        clCache = cls.size() > 0 ? cls.get(0) : null;
        return clCache;
    }

    public void setClassLoader(Integer id)
    {
        this.classLoader = id;
    }

    /**
     * If true, the instances created from this JobDef will be run inside a dedicated JVM instead of simply being a thread inside an engine.
     * Default is <code>false</code>.<br>
     * If using this, JVM options specific to this JobDef may be set through {@link #getJavaOpts()}.
     */
    public boolean isExternal()
    {
        return external;
    }

    /**
     * See {@link #isExternal()}
     */
    public void setExternal(boolean external)
    {
        this.external = external;
    }

    /**
     * If <code>false</code>, the instances created from this JobDef won't actually run: the engine will simply fake a successful run.<br>
     * Default is <code>true</code>
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * See {@link #isEnabled()}
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * This specifies how to interpret {@link #getJarPath()}.
     */
    public PathType getPathType()
    {
        return this.pathType;
    }

    /**
     * See {@link #getPathType()}
     */
    public void setPathType(PathType type)
    {
        this.pathType = type;
    }

    public Integer getPriority()
    {
        return priority;
    }

    public void setPriority(Integer priority)
    {
        this.priority = priority;
    }

    /**
     * ResultSet is not modified (no rs.next called).
     * 
     * @param rs
     * @return
     */
    static JobDef map(ResultSet rs, int colShift)
    {
        JobDef tmp = new JobDef();

        try
        {
            tmp.id = rs.getInt(1 + colShift);
            tmp.application = rs.getString(2 + colShift);
            tmp.applicationName = rs.getString(3 + colShift);
            tmp.canBeRestarted = true;
            tmp.classLoader = rs.getInt(4 + colShift) > 0 ? rs.getInt(4 + colShift) : null;
            tmp.description = rs.getString(5 + colShift);
            tmp.enabled = rs.getBoolean(6 + colShift);
            tmp.external = rs.getBoolean(7 + colShift);
            tmp.highlander = rs.getBoolean(8 + colShift);
            tmp.jarPath = rs.getString(9 + colShift);
            tmp.javaClassName = rs.getString(10 + colShift);
            tmp.javaOpts = rs.getString(11 + colShift);
            tmp.keyword1 = rs.getString(12 + colShift);
            tmp.keyword2 = rs.getString(13 + colShift);
            tmp.keyword3 = rs.getString(14 + colShift);
            tmp.maxTimeRunning = rs.getInt(15 + colShift);
            tmp.module = rs.getString(16 + colShift);
            tmp.pathType = PathType.valueOf(rs.getString(17 + colShift));
            tmp.queue_id = rs.getInt(18 + colShift);
            tmp.priority = rs.getInt(19 + colShift) > 0 ? rs.getInt(19 + colShift) : null;
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return tmp;
    }

    public static List<JobDef> select(DbConn cnx, String query_key, Object... args)
    {
        List<JobDef> res = new ArrayList<JobDef>();
        try
        {
            ResultSet rs = cnx.runSelect(query_key, args);
            while (rs.next())
            {
                JobDef tmp = map(rs, 0);
                res.add(tmp);
            }

            // TODO: pre fetch parameters as we always need them.
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static int create(DbConn cnx, String description, String javaClassName, Map<String, String> parameters, String jarPath,
            int queue_id, Integer maxTimeRunning, String applicationName, String application, String module, String keyword1,
            String keyword2, String keyword3, boolean highlander, Integer classLoaderId, PathType pathType)
    {
        QueryResult r = cnx.runUpdate("jd_insert", application, applicationName, classLoaderId, description, true, false, highlander,
                jarPath, javaClassName, null, keyword1, keyword2, keyword3, maxTimeRunning, module, pathType.toString(), queue_id);
        int newId = r.getGeneratedId();

        if (parameters != null)
        {
            for (Map.Entry<String, String> prm : parameters.entrySet())
            {
                cnx.runUpdate("jdprm_insert", prm.getKey(), prm.getValue(), newId);
            }
        }

        return newId;
    }

    public static JobDef select_key(DbConn cnx, String name)
    {
        List<JobDef> res = select(cnx, "jd_select_by_key", name);
        if (res.isEmpty())
        {
            throw new NoResultException("no result for query by key for key " + name);
        }
        if (res.size() > 1)
        {
            throw new DatabaseException("Inconsistent database! Multiple results for query by key for key " + name);
        }
        return res.get(0);
    }

    public void update(DbConn cnx, Map<String, String> parameters)
    {
        if (id == null)
        {
            this.id = JobDef.create(cnx, description, javaClassName, parameters, jarPath, queue_id, maxTimeRunning, applicationName,
                    application, module, keyword1, keyword2, keyword3, highlander, classLoader, pathType);
        }
        else
        {
            cnx.runUpdate("jd_update_all_fields_by_id", application, applicationName, description, enabled, external, highlander, jarPath,
                    javaClassName, javaOpts, keyword1, keyword2, keyword3, maxTimeRunning, module, pathType, classLoader, queue_id, id);
            cnx.runUpdate("jdprm_delete_all_for_jd", this.id);
            for (Map.Entry<String, String> prm : parameters.entrySet())
            {
                cnx.runUpdate("jdprm_insert", prm.getKey(), prm.getValue(), this.id);
            }
        }
    }

    public static void setExternal(DbConn cnx, Integer jdId)
    {
        cnx.runUpdate("jd_update_set_external_by_id", jdId);
    }
}
