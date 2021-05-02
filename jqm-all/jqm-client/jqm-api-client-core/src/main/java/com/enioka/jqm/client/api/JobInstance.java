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

package com.enioka.jqm.client.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Represents the result of a job execution request - either a queued request, or a running job, or the result of said execution.
 *
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class JobInstance
{
    private Integer id;
    private String applicationName;
    private Integer parent;
    private String user;
    private String sessionID;
    private State state;
    private Long position;
    private Integer priority;
    private Queue queue;
    private String queueName;
    private String keyword1, keyword2, keyword3, definitionKeyword1, definitionKeyword2, definitionKeyword3, module, email, application;
    private Map<String, String> parameters = new HashMap<>();
    private Integer progress;
    @XmlElementWrapper(name = "messages")
    @XmlElement(name = "message", type = String.class)
    private List<String> messages = new ArrayList<>();
    private Calendar enqueueDate, beganRunningDate, endDate, runAfter;
    private String nodeName;
    private boolean highlander;
    private boolean fromSchedule;

    /**
     * The Job Instance ID, i.e. the unique identifier of the execution request. This is a key for numerous {@link JqmClient} functions.
     */
    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    /**
     * The ID of the parent job that has enqueued this job instance. Null if the execution request was not done by a running job.
     */
    public Integer getParent()
    {
        return parent;
    }

    public void setParent(Integer parent)
    {
        this.parent = parent;
    }

    /**
     * The user name that was given at enqueue time. Optional.
     */
    public String getUser()
    {
        return user;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    /**
     * The session ID that was given at enqueue time. Optional.
     */
    public String getSessionID()
    {
        return sessionID;
    }

    public void setSessionID(String sessionID)
    {
        this.sessionID = sessionID;
    }

    /**
     * Status of the job. Usual cycle: SUBMITTED -> ATTRIBUTED -> RUNNING, -> DONE or CRASHED.
     */
    public State getState()
    {
        return state;
    }

    public void setState(State state)
    {
        this.state = state;
    }

    /**
     * Position in the queue. 0 if running.<br>
     * <strong>This is the value retrieved during the latest {@link JqmClient#getJob(int)} call and may not be up to date!</strong>
     */
    public Long getPosition()
    {
        return position;
    }

    public void setPosition(Long position)
    {
        this.position = position;
    }

    /**
     * The queue in which the job was enqueued. Will be null if the queue has been deleted. In that case use {@link #getQueueName()}.
     */
    public Queue getQueue()
    {
        return queue;
    }

    public void setQueue(Queue queue)
    {
        this.queue = queue;
    }

    /**
     * The queue in which the job was enqueued.
     */
    public String getQueueName()
    {
        return queueName;
    }

    /**
     * See {@link #getQueueName()}
     */
    public void setQueueName(String queueName)
    {
        this.queueName = queueName;
    }

    /**
     * A list of all the parameters used by this job (both those passed at enqueue time and those defined as default parameters for this
     * kind of jobs)
     */
    public Map<String, String> getParameters()
    {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters)
    {
        this.parameters = parameters;
    }

    /**
     * An optional integer that running user code may update from time to time. Used to give an idea of the progress of the job instance.
     * <br>
     * <strong>This is the value retrieved during the latest {@link JqmClient#getJob(int)} call and may not be up to date!</strong>
     */
    public Integer getProgress()
    {
        return progress;
    }

    public void setProgress(Integer progress)
    {
        this.progress = progress;
    }

    /**
     * An optional list of strings that running user code may emit from time to time. Used to give an idea of the progress of the job
     * instance.<br>
     * <strong>This is the value retrieved during the latest {@link JqmClient#getJob(int)} call and may not be up to date!</strong>
     */
    public List<String> getMessages()
    {
        return messages;
    }

    public void setMessages(List<String> messages)
    {
        this.messages = messages;
    }

    /**
     * An optional classification tag which can be specified inside the execution request (default is NULL).
     */
    public String getKeyword1()
    {
        return keyword1;
    }

    public void setKeyword1(String keyword1)
    {
        this.keyword1 = keyword1;
    }

    /**
     * An optional classification tag which can be specified inside the execution request (default is NULL).
     */
    public String getKeyword2()
    {
        return keyword2;
    }

    public void setKeyword2(String keyword2)
    {
        this.keyword2 = keyword2;
    }

    /**
     * An optional classification tag which can be specified inside the execution request (default is NULL).
     */
    public String getKeyword3()
    {
        return keyword3;
    }

    public void setKeyword3(String keyword3)
    {
        this.keyword3 = keyword3;
    }

    /**
     * An optional classification tag which can be specified inside the definition of the job (default is NULL).
     */
    public String getDefinitionKeyword1()
    {
        return definitionKeyword1;
    }

    public void setDefinitionKeyword1(String keyword1)
    {
        this.definitionKeyword1 = keyword1;
    }

    /**
     * An optional classification tag which can be specified inside the definition of the job (default is NULL).
     */
    public String getDefinitionKeyword2()
    {
        return definitionKeyword2;
    }

    public void setDefinitionKeyword2(String keyword2)
    {
        this.definitionKeyword2 = keyword2;
    }

    /**
     * An optional classification tag which can be specified inside the definition of the job (default is NULL).
     */
    public String getDefinitionKeyword3()
    {
        return definitionKeyword3;
    }

    public void setDefinitionKeyword3(String keyword3)
    {
        this.definitionKeyword3 = keyword3;
    }

    /**
     * An optional classification tag which can be specified inside the execution request (default is NULL).
     */
    public String getApplication()
    {
        return application;
    }

    public void setApplication(String application)
    {
        this.application = application;
    }

    /**
     * The functional key that identifies a job definition template (a JobDef, as imported from XML).
     */
    public String getApplicationName()
    {
        return applicationName;
    }

    public void setApplicationName(String applicationName)
    {
        this.applicationName = applicationName;
    }

    public String getModule()
    {
        return module;
    }

    public void setModule(String module)
    {
        this.module = module;
    }

    /**
     * If this field is non-null, an e-mail will be sent at this address at the end of the run.
     */
    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = email;
    }

    /**
     * The time at which the execution request was given to {@link JqmClient#enqueue(JobRequest)}.
     */
    public Calendar getEnqueueDate()
    {
        return enqueueDate;
    }

    public void setEnqueueDate(Calendar enqueueDate)
    {
        this.enqueueDate = enqueueDate;
    }

    /**
     * The time at which the execution really began (the request arrived at the top of the queue and was run by an engine).
     */
    public Calendar getBeganRunningDate()
    {
        return beganRunningDate;
    }

    public void setBeganRunningDate(Calendar beganRunningDate)
    {
        this.beganRunningDate = beganRunningDate;
    }

    /**
     * The time at which the execution ended, resulting in an ENDED or CRASHED status.
     */
    public Calendar getEndDate()
    {
        return endDate;
    }

    public void setEndDate(Calendar endDate)
    {
        this.endDate = endDate;
    }

    /**
     * The name of the JQM node that is running or has run the job instance. This is a String that logically identifies the node, not the
     * hostname.
     */
    public String getNodeName()
    {
        return nodeName;
    }

    public void setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
    }

    /**
     * True if the job instance was subject to the Highlander mode rules.
     */
    public boolean isHighlander()
    {
        return highlander;
    }

    public void setHighlander(boolean h)
    {
        this.highlander = h;
    }

    /**
     * True if the job instance was created from a schedule.
     */
    public boolean isFromSchedule()
    {
        return this.fromSchedule;
    }

    public void setFromSchedule(boolean b)
    {
        this.fromSchedule = b;
    }

    /**
     * The thread and queue priority of the job instance.
     */
    public Integer getPriority()
    {
        return priority;
    }

    public void setPriority(Integer priority)
    {
        this.priority = priority;
    }

    /**
     * An optional date in the future after which JQLM should try to run the job. It is always null for ended job instances.
     */
    public Calendar getRunAfter()
    {
        return this.runAfter;
    }

    public void setRunAfter(Calendar c)
    {
        this.runAfter = c;
    }
}
