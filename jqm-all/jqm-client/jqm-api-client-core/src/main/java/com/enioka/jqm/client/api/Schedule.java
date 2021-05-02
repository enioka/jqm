package com.enioka.jqm.client.api;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The definition of a recurrence associated with a job definition. This is a read only object designed to help with calling some
 * {@link JqmClient} verbs.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Schedule implements Serializable
{
    private static final long serialVersionUID = -8520223502712009225L;

    private int id;
    private String cronExpression;
    private Integer priority;
    private Queue queue;
    private Map<String, String> parameters = new HashMap<>();

    /**
     * The schedule ID. This is the same ID returned by {@link JqmClient#enqueue(JobRequest)}.
     */
    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    /**
     * The recurrence expression, in cron syntax.
     */
    public String getCronExpression()
    {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression)
    {
        this.cronExpression = cronExpression;
    }

    /**
     * Optional. This is an overload of the default priority of the job definition (itself optional).
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
     * Optional. This is an overload of the default queue of the job definition.
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
     * A set of parameters (key/value pairs) which are available to job instances at runtime. Parameters can be defined at multiple levels:
     * here, inside the job definition, and inside the {@link JobRequest}. In case a parameter key exists at multiple levels, the parameter
     * defined here has the least priority.
     */
    public Map<String, String> getParameters()
    {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters)
    {
        this.parameters = parameters;
    }

}
