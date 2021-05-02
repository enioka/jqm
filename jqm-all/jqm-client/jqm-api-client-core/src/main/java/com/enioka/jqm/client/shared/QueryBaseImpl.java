/**
 * Copyright © 2013 enioka. All rights reserved
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
package com.enioka.jqm.client.shared;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.Query;
import com.enioka.jqm.client.api.State;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 *
 * Base abstract implementation of {@link Query}. Deriving types should add fluent construction APIs (which need a client implementation not
 * available in the API itself).
 */
public class QueryBaseImpl implements Query
{
    protected Integer jobInstanceId, parentId;
    protected List<String> applicationName = new ArrayList<>();
    protected String user, sessionId;
    protected String jobDefKeyword1, jobDefKeyword2, jobDefKeyword3, jobDefModule, jobDefApplication;
    protected String instanceKeyword1, instanceKeyword2, instanceKeyword3, instanceModule, instanceApplication;
    protected String queueName, nodeName;
    protected Integer queueId;
    protected Calendar enqueuedBefore, enqueuedAfter, beganRunningBefore, beganRunningAfter, endedBefore, endedAfter;

    protected List<State> status = new ArrayList<>();

    protected Integer firstRow, pageSize = 50;

    protected List<SortSpec> sorts = new ArrayList<>();

    protected boolean queryLiveInstances = false, queryHistoryInstances = true;

    protected Integer resultSize;
    protected List<JobInstance> results;

    protected JqmClientQuerySubmitCallback parentClient;

    // //////////////////////////////////////////
    // Construction
    // //////////////////////////////////////////

    public QueryBaseImpl(JqmClientQuerySubmitCallback client)
    {
        this.parentClient = client;
    }

    // //////////////////////////////////////////
    // Execution
    // //////////////////////////////////////////

    @Override
    public List<JobInstance> invoke()
    {
        return this.parentClient.getJobs(this);
    }

    // //////////////////////////////////////////
    // Sort
    // //////////////////////////////////////////

    @Override
    public Query addSortAsc(Sort column)
    {
        this.sorts.add(new SortSpec(SortOrder.ASCENDING, column));
        return this;
    }

    @Override
    public Query addSortDesc(Sort column)
    {
        this.sorts.add(new SortSpec(SortOrder.DESCENDING, column));
        return this;
    }

    public List<SortSpec> getSorts()
    {
        return this.sorts;
    }

    // //////////////////////////////////////////
    // Results handling
    // //////////////////////////////////////////

    @Override
    public Query setPageSize(Integer pageSize)
    {
        this.pageSize = pageSize;
        return this;
    }

    @Override
    public Query setFirstRow(Integer firstRow)
    {
        this.firstRow = firstRow;
        return this;
    }

    @Override
    public Integer getResultSize()
    {
        if (results == null)
        {
            throw new IllegalStateException("Cannot retrieve the results of a query that was not run");
        }
        if (resultSize != null)
        {
            return resultSize;
        }
        else
        {
            return results.size();
        }
    }

    public void setResultSize(Integer resultSize)
    {
        this.resultSize = resultSize;
    }

    @Override
    public List<JobInstance> getResults()
    {
        if (results == null)
        {
            throw new IllegalStateException("Cannot retrieve the results of a query that was not run");
        }
        return results;
    }

    public void setResults(List<JobInstance> results)
    {
        this.results = results;
    }

    // //////////////////////////////////////////
    // Stupid get/set
    // //////////////////////////////////////////

    public Integer getJobInstanceId()
    {
        return jobInstanceId;
    }

    @Override
    public Query setJobInstanceId(Integer jobInstanceId)
    {
        this.jobInstanceId = jobInstanceId;
        return this;
    }

    public Integer getParentId()
    {
        return parentId;
    }

    @Override
    public Query setParentId(Integer parentId)
    {
        this.parentId = parentId;
        return this;
    }

    public List<String> getApplicationName()
    {
        return applicationName;
    }

    @Override
    public Query setApplicationName(List<String> applicationName)
    {
        this.applicationName = applicationName;
        return this;
    }

    @Override
    public Query setApplicationName(String applicationName)
    {
        this.applicationName.clear();
        this.applicationName.add(applicationName);
        return this;
    }

    public String getUser()
    {
        return user;
    }

    @Override
    public Query setUser(String user)
    {
        this.user = user;
        return this;
    }

    public String getSessionId()
    {
        return sessionId;
    }

    @Override
    public Query setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
        return this;
    }

    public String getJobDefKeyword1()
    {
        return jobDefKeyword1;
    }

    @Override
    public Query setJobDefKeyword1(String jobDefKeyword1)
    {
        this.jobDefKeyword1 = jobDefKeyword1;
        return this;
    }

    public String getJobDefKeyword2()
    {
        return jobDefKeyword2;
    }

    @Override
    public Query setJobDefKeyword2(String jobDefKeyword2)
    {
        this.jobDefKeyword2 = jobDefKeyword2;
        return this;
    }

    public String getJobDefKeyword3()
    {
        return jobDefKeyword3;
    }

    @Override
    public Query setJobDefKeyword3(String jobDefKeyword3)
    {
        this.jobDefKeyword3 = jobDefKeyword3;
        return this;
    }

    public String getJobDefModule()
    {
        return jobDefModule;
    }

    @Override
    public Query setJobDefModule(String jobDefModule)
    {
        this.jobDefModule = jobDefModule;
        return this;
    }

    public String getJobDefApplication()
    {
        return jobDefApplication;
    }

    @Override
    public Query setJobDefApplication(String jobDefApplication)
    {
        this.jobDefApplication = jobDefApplication;
        return this;
    }

    public String getInstanceKeyword1()
    {
        return instanceKeyword1;
    }

    @Override
    public Query setInstanceKeyword1(String instanceKeyword1)
    {
        this.instanceKeyword1 = instanceKeyword1;
        return this;
    }

    public String getInstanceKeyword2()
    {
        return instanceKeyword2;
    }

    @Override
    public Query setInstanceKeyword2(String instanceKeyword2)
    {
        this.instanceKeyword2 = instanceKeyword2;
        return this;
    }

    public String getInstanceKeyword3()
    {
        return instanceKeyword3;
    }

    @Override
    public Query setInstanceKeyword3(String instanceKeyword3)
    {
        this.instanceKeyword3 = instanceKeyword3;
        return this;
    }

    public String getInstanceModule()
    {
        return instanceModule;
    }

    @Override
    public Query setInstanceModule(String instanceModule)
    {
        this.instanceModule = instanceModule;
        return this;
    }

    public String getInstanceApplication()
    {
        return instanceApplication;
    }

    @Override
    public Query setInstanceApplication(String instanceApplication)
    {
        this.instanceApplication = instanceApplication;
        return this;
    }

    public boolean isQueryLiveInstances()
    {
        return queryLiveInstances;
    }

    @Override
    public Query setQueryLiveInstances(boolean queryLiveInstances)
    {
        this.queryLiveInstances = queryLiveInstances;
        if (this.queryHistoryInstances)
        {
            this.pageSize = null;
            this.firstRow = null;
        }
        return this;
    }

    public boolean isQueryHistoryInstances()
    {
        return queryHistoryInstances;
    }

    @Override
    public Query setQueryHistoryInstances(boolean queryHistoryInstances)
    {
        this.queryHistoryInstances = queryHistoryInstances;
        return this;
    }

    public Calendar getEnqueuedBefore()
    {
        return enqueuedBefore;
    }

    @Override
    public Query setEnqueuedBefore(Calendar enqueuedBefore)
    {
        this.enqueuedBefore = enqueuedBefore;
        return this;
    }

    public Calendar getEnqueuedAfter()
    {
        return enqueuedAfter;
    }

    @Override
    public Query setEnqueuedAfter(Calendar enqueuedAfter)
    {
        this.enqueuedAfter = enqueuedAfter;
        return this;
    }

    public Calendar getBeganRunningBefore()
    {
        return beganRunningBefore;
    }

    @Override
    public Query setBeganRunningBefore(Calendar beganRunningBefore)
    {
        this.beganRunningBefore = beganRunningBefore;
        return this;
    }

    public Calendar getBeganRunningAfter()
    {
        return beganRunningAfter;
    }

    @Override
    public Query setBeganRunningAfter(Calendar beganRunningAfter)
    {
        this.beganRunningAfter = beganRunningAfter;
        return this;
    }

    public Calendar getEndedBefore()
    {
        return endedBefore;
    }

    @Override
    public Query setEndedBefore(Calendar endedBefore)
    {
        this.endedBefore = endedBefore;
        return this;
    }

    public Calendar getEndedAfter()
    {
        return endedAfter;
    }

    @Override
    public Query setEndedAfter(Calendar endedAfter)
    {
        this.endedAfter = endedAfter;
        return this;
    }

    public List<State> getStatus()
    {
        return status;
    }

    @Override
    public Query addStatusFilter(State status)
    {
        this.status.add(status);
        return this;
    }

    public Integer getFirstRow()
    {
        return firstRow;
    }

    public Integer getPageSize()
    {
        return pageSize;
    }

    public String getQueueName()
    {
        return queueName;
    }

    @Override
    public Query setQueueName(String queueName)
    {
        this.queueName = queueName;
        return this;
    }

    public Integer getQueueId()
    {
        return queueId;
    }

    @Override
    public Query setQueueId(Integer queueId)
    {
        this.queueId = queueId;
        return this;
    }

    @Override
    public Query setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
        return this;
    }

    public String getNodeName()
    {
        return nodeName;
    }
}
