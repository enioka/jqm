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
package com.enioka.jqm.client.api;

import java.util.Calendar;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.enioka.jqm.client.shared.QueryBaseImpl;

/**
 * Parameters for querying {@link JobInstance}s. A null parameter (the default for most QueryInterface parameters) is ignored in the query.
 * To query a null String, specify "" (empty String). To QueryInterface a null Integer, specify -1. It is not possible to QueryInterface for
 * null Calendar values, since it is far more efficient to QueryInterface by status (the different Calendar fields are only null at certain
 * statuses).<br>
 * See individual setters for the signification of QueryInterface parameters.<br>
 * <br>
 * By default, i.e. by simply using <code>JqmClientFactory.getClient().newQuery().invoke()</code>, the API retrieves the first 50 instances
 * that have ended, ordered by launch ID (i.e. by time). See {@link QueryBaseImpl#setQueryLiveInstances(boolean)} for details and how to
 * retrieve living instances in addition to ended ones.<br>
 * <br>
 *
 * Also please note that queries get more expensive with the result count, so it is <strong>strongly recommended to use pagination</strong>
 * ({@link #setFirstRow(Integer)} and {@link #setPageSize(Integer)}).
 *
 */
public interface Query
{
    /**
     * The different fields that can be used in sorting.
     */
    public static enum Sort {
        ID("id"), APPLICATIONNAME("JD_KEY"), QUEUENAME("QUEUE_NAME"), STATUS("STATUS"), DATEENQUEUE("DATE_ENQUEUE"), DATEATTRIBUTION(
                "DATE_ATTRIBUTION"), DATEEXECUTION("DATE_START"), DATEEND("DATE_END", null), USERNAME("USERNAME"), PARENTID("PARENT");

        private String historyField, jiField;

        private Sort(String historyField, String jiField)
        {
            this.historyField = historyField;
            this.jiField = jiField;
        }

        private Sort(String commonField)
        {
            this.historyField = commonField;
            this.jiField = commonField;
        }

        public String getHistoryField()
        {
            return this.historyField;
        }

        public String getJiField()
        {
            return this.jiField;
        }
    }

    /**
     * The sort order
     */
    public static enum SortOrder {
        ASCENDING, DESCENDING;
    }

    /**
     * Internal description of a sorting operation
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SortSpec
    {
        public SortOrder order = SortOrder.ASCENDING;
        public Sort col;

        // Bean convention
        @SuppressWarnings("unused")
        private SortSpec()
        {

        }

        public SortSpec(SortOrder order, Sort column)
        {
            this.order = order;
            this.col = column;
        }
    }

    /**
     * Adds a new column a the end of the sorting clause.
     *
     * @see #addSortDesc(Sort)
     */
    public Query addSortAsc(Sort column);

    /**
     * Adds a new column a the end of the sorting clause.
     *
     * @see #addSortAsc(Sort)
     */
    public Query addSortDesc(Sort column);

    // //////////////////////////////////////////
    // Builder
    // //////////////////////////////////////////

    /**
     * The end of the fluent QueryInterface API. It simply executes the Query and returns the results.
     */
    public List<JobInstance> invoke();

    // //////////////////////////////////////////
    // Results handling
    // //////////////////////////////////////////

    /**
     * This sets the maximum returned results count, for pagination purposes.<br>
     * It is <strong>highly recommended to use pagination</strong> when using the QueryInterface API, since queries are expensive.
     *
     * @param pageSize
     *            the maximal result count, or null for no limit (dangerous!)
     * @return the QueryInterface itself (fluent API - used to chain calls).
     * @see #setFirstRow(Integer) setFirstRow for the other pagination parameter.
     */
    public Query setPageSize(Integer pageSize);

    /**
     * This sets the starting row returned by the query, for pagination purposes. Note that even if order is very important for paginated
     * queries (to ensure that the pages stay the same between calls for new pages), a default sort is used if none is specified.<br>
     * It is <strong>highly recommended to use pagination</strong> when using the QueryInterface API, since queries are expensive.
     *
     * @param firstRow
     *            the first row to return. 0 is equivalent to null.
     * @return the QueryInterface itself (fluent API - used to chain calls).
     * @see #setPageSize(Integer) setPageSize for the other pagination parameter.
     */
    public Query setFirstRow(Integer firstRow);

    /**
     * @return the available result count of the query. Available means that it does not take into account pagination. This is mostly used
     *         when pagination is used, so as to be able to set a "total records count" or a "page 2 on 234" indicator. If pagination is not
     *         used, this is always equal to <code>{@link #getResults()}.size()</code>.
     */
    public Integer getResultSize();

    public List<JobInstance> getResults();

    // //////////////////////////////////////////
    // Stupid get/set
    // //////////////////////////////////////////

    /**
     * To QueryInterface a specific job instance. This ID is returned, for example, by the {@link JqmClient#enqueue(JobRequest)} method.
     * <br>
     * It is pretty useless to give any other QueryInterface parameters if you know the ID. Also note that there is a shortcut method named
     * {@link JqmClient#getJob(int)} to make a QueryInterface by ID.
     *
     * @param jobInstanceId
     *            the job instance ID
     */
    public Query setJobInstanceId(Integer jobInstanceId);

    /**
     * Some job instances are launched by other job instances (linked jobs which launch one another). This allows to QueryInterface all job
     * instances launched by a specific job instance.
     *
     * @param parentId
     *            the ID of the parent job instance.
     */
    public Query setParentId(Integer parentId);

    /**
     * The application name is the name of the job definition - the same name that is given in the Job Definition XML. This allows to query
     * all job instances for given job definitions. If the list contains multiple names, an OR QueryInterface takes place.
     *
     * @param applicationName
     */
    public Query setApplicationName(List<String> applicationName);

    /**
     * The application name is the name of the job definition - the same name that is given in the Job Definition XML. This allows to query
     * all job instances for a single given job definition. If other names were given previously (e.g. with
     * {@link #setApplicationName(List)} , they are removed by this method.
     *
     * @param applicationName
     * @return
     */
    public Query setApplicationName(String applicationName);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param user
     */
    public Query setUser(String user);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param sessionId
     */
    public Query setSessionId(String sessionId);

    /**
     * Optionally, it is possible to specify some classification data inside the Job Definition (usually through the import of a JobDef XML
     * file). This data exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param jobDefKeyword1
     */
    public Query setJobDefKeyword1(String jobDefKeyword1);

    /**
     * Optionally, it is possible to specify some classification data inside the Job Definition (usually through the import of a JobDef XML
     * file). This data exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param jobDefKeyword2
     */
    public Query setJobDefKeyword2(String jobDefKeyword2);

    /**
     * Optionally, it is possible to specify some classification data inside the Job Definition (usually through the import of a JobDef XML
     * file). This data exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param jobDefKeyword3
     */
    public Query setJobDefKeyword3(String jobDefKeyword3);

    /**
     * Optionally, it is possible to specify some classification data inside the Job Definition (usually through the import of a JobDef XML
     * file). This data exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param jobDefModule
     */
    public Query setJobDefModule(String jobDefModule);

    /**
     * Optionally, it is possible to specify some classification data inside the Job Definition (usually through the import of a JobDef XML
     * file). This data exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     * <br>
     * <strong>This has nothing to so with applicationName, which is the name of the Job Definition !</strong>
     *
     * @param jobDefApplication
     */
    public Query setJobDefApplication(String jobDefApplication);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param instanceKeyword1
     */
    public Query setInstanceKeyword1(String instanceKeyword1);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param instanceKeyword2
     */
    public Query setInstanceKeyword2(String instanceKeyword2);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param instanceKeyword3
     */
    public Query setInstanceKeyword3(String instanceKeyword3);

    public String getInstanceModule();

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying.
     *
     * @param instanceModule
     */
    public Query setInstanceModule(String instanceModule);

    /**
     * Optionally, it is possible to specify some classification data at enqueue time (inside the {@link JobRequest} object). This data
     * exists solely for later querying (no signification whatsoever for JQM itself). This parameter allows such querying. <br>
     * <strong>This has nothing to so with applicationName, which is the name of the Job Definition !</strong>
     *
     * @param instanceApplication
     */
    public Query setInstanceApplication(String instanceApplication);

    /**
     * By default, querying only occurs on ended (OK or not) job instances. If this parameter is set to true, it will also include living
     * (waiting, running, ...) job instances.<br>
     * If you also QueryInterface on live instances at the same time, this will reset pagination as it is impossible to use pagination with
     * both.<br>
     * <br>
     * Setting this to true has a noticeable performance impact and should be used as little as possible (or should be used when
     * {@link #setQueryHistoryInstances(boolean)} is false, which is not the default)
     */
    public Query setQueryLiveInstances(boolean queryLiveInstances);

    /**
     * By default, querying only occurs on ended (OK or not) job instances. If this parameter is set to false, however, the History will not
     * be used. This is usually used in conjunction with {@link #setQueryLiveInstances(boolean)}<br>
     * <br>
     */
    public Query setQueryHistoryInstances(boolean queryHistoryInstances);

    /**
     * The time at which the execution request was given to {@link JqmClient#enqueue(JobRequest)}. This is an <= comparison.
     *
     * @param enqueuedBefore
     */
    public Query setEnqueuedBefore(Calendar enqueuedBefore);

    /**
     * The time at which the execution request was given to {@link JqmClient#enqueue(JobRequest)}. This is an >= comparison.
     *
     * @param enqueuedAfter
     */
    public Query setEnqueuedAfter(Calendar enqueuedAfter);

    /**
     * The time at which the execution really began (the request arrived at the top of the queue and was run by an engine). This is an <=
     * comparison.
     *
     * @param beganRunningBefore
     */
    public Query setBeganRunningBefore(Calendar beganRunningBefore);

    /**
     * The time at which the execution really began (the request arrived at the top of the queue and was run by an engine). This is an >=
     * comparison.
     *
     * @param beganRunningAfter
     */
    public Query setBeganRunningAfter(Calendar beganRunningAfter);

    /**
     * The time at which the execution ended, resulting in an ENDED or CRASHED status. This is an <= comparison.
     *
     * @param endedBefore
     */
    public Query setEndedBefore(Calendar endedBefore);

    /**
     * The time at which the execution ended, resulting in an ENDED or CRASHED status. This is an <= comparison.
     *
     * @param endedAfter
     */
    public Query setEndedAfter(Calendar endedAfter);

    /**
     * Filter by status. See {@link State} for the different possible values and their meaning. If multiple values are added, a logical OR
     * will take place.
     *
     * @param status
     */
    public Query addStatusFilter(State status);

    /**
     * For querying jobs on a given queue. The list of queues can be retrieved through {@link JqmClient#getQueues()}.
     */
    public Query setQueueName(String queueName);

    /**
     * For querying jobs on a given queue. The list of queues can be retrieved through {@link JqmClient#getQueues()}.<br>
     * Ignored if setQueueName is used.
     */
    public Query setQueueId(Integer queueId);

    /**
     * For querying jobs that have run or are running on a specific JQM node.
     */
    public Query setNodeName(String nodeName);
}
