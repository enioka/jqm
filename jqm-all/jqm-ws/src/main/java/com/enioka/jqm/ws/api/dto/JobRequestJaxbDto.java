package com.enioka.jqm.ws.api.dto;

import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.api.State;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class JobRequestJaxbDto implements Serializable
{
    private static final long serialVersionUID = -2289379952629706591L;

    private String applicationName;
    private String sessionID;
    private String application;
    private String user;
    private String module;
    private String keyword1;
    private String keyword2;
    private String keyword3;
    private String email = null;
    private String queueName = null;
    private Integer parentJobId = null;
    private Integer scheduleId = null;
    private State startState = null;
    private Integer priority = 0;
    private Map<String, String> parameters = new HashMap<>();

    private Calendar runAfter;
    private String recurrence;

    // Convention
    public JobRequestJaxbDto()
    {}

    public void copyTo(JobRequest target)
    {
        target.setApplication(application);
        target.setApplicationName(applicationName);
        target.setEmail(email);
        target.setKeyword1(keyword1);
        target.setKeyword2(keyword2);
        target.setKeyword3(keyword3);
        target.setModule(module);
        target.setParameters(parameters);
        target.setParentID(parentJobId);
        target.setPriority(priority);
        target.setQueueName(queueName);
        target.setRecurrence(recurrence);
        target.setRunAfter(runAfter);
        target.setScheduleId(scheduleId);
        target.setSessionID(sessionID);
        target.setUser(user);

        if (startState == State.HOLDED)
        {
            target.startHeld();
        }
    }

    public JobInstance toJobInstance(int newId)
    {
        JobInstance ji = new JobInstance();

        ji.setId(newId);
        ji.setKeyword1(keyword1);
        ji.setKeyword2(keyword2);
        ji.setKeyword3(keyword3);
        ji.setParameters(parameters);
        ji.setParent(parentJobId);
        ji.setSessionID(sessionID);
        ji.setState(State.SUBMITTED);
        ji.setUser(user);
        ji.setPosition(Long.MAX_VALUE);
        ji.setApplication(application);

        return ji;
    }
}
