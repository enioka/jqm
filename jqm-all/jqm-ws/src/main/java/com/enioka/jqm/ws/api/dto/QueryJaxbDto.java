package com.enioka.jqm.ws.api.dto;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.api.Query.SortSpec;
import com.enioka.jqm.client.api.State;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class QueryJaxbDto
{
    private Integer jobInstanceId, parentId;
    private List<String> applicationName = new ArrayList<>();
    private String user, sessionId;
    private String jobDefKeyword1, jobDefKeyword2, jobDefKeyword3, jobDefModule, jobDefApplication;
    private String instanceKeyword1, instanceKeyword2, instanceKeyword3, instanceModule, instanceApplication;
    private String queueName, nodeName;
    private Integer queueId;
    private Calendar enqueuedBefore, enqueuedAfter, beganRunningBefore, beganRunningAfter, endedBefore, endedAfter;

    @XmlElementWrapper(name = "statuses")
    @XmlElement(name = "status", type = State.class)
    private List<State> status = new ArrayList<>();

    private Integer firstRow, pageSize = 50;
    @SuppressWarnings("unused")
    private Integer resultSize;

    @XmlElementWrapper(name = "instances")
    @XmlElement(name = "instance", type = JobInstance.class)
    private List<JobInstance> results;

    @XmlElementWrapper(name = "sortby")
    @XmlElement(name = "sortitem", type = SortSpec.class)
    private List<SortSpec> sorts = new ArrayList<>();

    private boolean queryLiveInstances = false, queryHistoryInstances = true;

    public void copyTo(Query target)
    {
        target.setApplicationName(applicationName);
        target.setBeganRunningAfter(beganRunningAfter);
        target.setBeganRunningBefore(beganRunningBefore);
        target.setEndedAfter(endedAfter);
        target.setEndedBefore(endedBefore);
        target.setEnqueuedAfter(enqueuedAfter);
        target.setEnqueuedBefore(enqueuedBefore);
        target.setFirstRow(firstRow);
        target.setInstanceApplication(instanceApplication);
        target.setInstanceKeyword1(instanceKeyword1);
        target.setInstanceKeyword2(instanceKeyword2);
        target.setInstanceKeyword3(instanceKeyword3);
        target.setInstanceModule(instanceModule);
        target.setJobDefApplication(jobDefApplication);
        target.setJobDefKeyword1(jobDefKeyword1);
        target.setJobDefKeyword2(jobDefKeyword2);
        target.setJobDefKeyword3(jobDefKeyword3);
        target.setJobDefModule(jobDefModule);
        target.setJobInstanceId(jobInstanceId);
        target.setNodeName(nodeName);
        target.setPageSize(pageSize);
        target.setParentId(parentId);
        target.setQueryHistoryInstances(queryHistoryInstances);
        target.setQueryLiveInstances(queryLiveInstances);
        target.setQueueId(queueId);
        target.setQueueName(queueName);
        // target.setResultSize(resultSize);
        // target.setResults(results);
        target.setSessionId(sessionId);
        target.setUser(user);
    }

    public void updateResults(Query q)
    {
        this.resultSize = q.getResultSize();
        this.results = q.getResults();
    }
}
