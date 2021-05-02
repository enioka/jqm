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

import java.io.Serializable;
import java.util.Calendar;
import java.util.Map;

/**
 * Job execution request. It contains the job application name (the only mandatory piece of data needed to enqueue a request), as well as
 * non-mandatory data. It is consumed by {@link JobRequest#enqueue()}
 */
public interface JobRequest extends Serializable
{
    /**
     * Submit the request to the JQM cluster (end of the fluent API).
     *
     * @throws JqmInvalidRequestException
     *             when input data is invalid.
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     * @return the ID of the job instance.
     */
    public Integer enqueue();

    /**
     * Parameters are <key,value> pairs that are passed at runtime to the job. The amount of required parameters depends on the requested
     * job itself.
     *
     * @param key
     *            max length is 50
     * @param value
     *            max length is 1000
     */
    public JobRequest addParameter(String key, String value);

    /**
     * See {@link #addParameter(String, String)}
     *
     * @param prms
     * @return
     */
    public JobRequest addParameters(Map<String, String> prms);

    /**
     * Parameters are <key,value> pairs that are passed at runtime to the job. The amount of required parameters depends on the requested
     * job itself. If there is no parameter named key, no error is thrown.
     *
     * @param key
     */
    public void delParameter(String key);

    /**
     * <strong>Compulsory</strong> (unless {@link #setScheduleId(Integer)} is used)<br>
     * The name of the batch job to launch. It is the "Job Definition" name, and the most important parameter in this form.
     *
     * @param applicationName
     *            max length is 100
     */
    public JobRequest setApplicationName(String applicationName);

    /**
     * <strong>Optional</strong><br>
     * It is possible to link a job instance to an arbitrary ID, such as a session ID and later query result by this ID.<br>
     * Default is null.
     *
     * @param sessionID
     *            max length is 100
     */
    public JobRequest setSessionID(String sessionID);

    /**
     * <strong>Optional</strong><br>
     * The application making the query. E.g.: Accounting, Interfaces, ...
     *
     * @param application
     *            max length is 50
     */
    public JobRequest setApplication(String application);

    /**
     * <strong>Optional</strong><br>
     * An optional classification axis (and therefore query criterion)
     *
     * @param module
     *            max length is 50
     */
    public JobRequest setModule(String module);

    /**
     * <strong>Optional</strong><br>
     * An optional classification axis (and therefore query criterion)
     *
     * @param keyword1
     *            max length is 50
     */
    public JobRequest setKeyword1(String keyword1);

    /**
     * <strong>Optional</strong><br>
     * An optional classification axis (and therefore query criterion)
     *
     * @param keyword2
     *            max length is 50
     */
    public JobRequest setKeyword2(String keyword2);

    /**
     * <strong>Optional</strong><br>
     * An optional classification axis (and therefore query criterion)
     *
     * @param keyword3
     *            max length is 50
     */
    public JobRequest setKeyword3(String keyword3);

    /**
     * Parameters are <key,value> pairs that are passed at runtime to the job. The amount of required parameters depends on the requested
     * job itself. This method allows to set them all at once instead of calling {@link #addParameter(String, String)} multiple times.<br>
     * This methods removes all previously set parameters.
     *
     * @param parameters
     *            dictionary of all parameters.
     */
    public JobRequest setParameters(Map<String, String> parameters);

    /**
     * <strong>Optional</strong><br>
     * It is possible to associate a user to a job execution request, and later query job execution by user.
     *
     * @param user
     *            max length is 50
     */
    public JobRequest setUser(String user);

    /**
     * <strong>Optional</strong><br>
     * The user can enter an email to receive an email when the job is ended.
     *
     * @param email
     *            max length is 100
     */
    public JobRequest setEmail(String email);

    /**
     * <strong>Optional</strong><br>
     * A job instance can be the child of another job instance. This allows you to set the ID of that parent. It should be left null if
     * there is no parent.
     */
    public JobRequest setParentJobId(String parentJobId);

    /**
     * <strong>Optional</strong><br>
     * A job instance can be the child of another job instance. This allows you to set the ID of that parent. It should be left null if
     * there is no parent.
     */
    public JobRequest setParentID(Integer parentJobId);

    /**
     * <strong>Optional</strong><br>
     * The (FIFO) queue inside which the job request should wait for a free execution slot inside an engine. If null, the queue designated
     * as the default queue for this "application name" will be used.<br>
     * <strong>Most of the time, this should be left to null.</strong> This parameter is only provided to avoid doing two API calls for a
     * single execution request (first enqueue, then change queue) when it is certain a specific queue will have to be used.<br>
     * If there is no queue of this name, the enqueue method will throw a <code>JqmInvalidRequestException</code>.
     */
    public JobRequest setQueueName(String queueName);

    /**
     * <strong>Optional</strong><br>
     * This request is actually to create an occurrence of the specified recurrence. If specified, the {@link #getApplicationName()} is
     * ignored.
     */
    public JobRequest setScheduleId(Integer id);

    /**
     * <strong>Optional</strong><br>
     * The default behaviour for a newly submitted JobRequest is to run as soon as possible (i.e. as soon as there is a free slot inside a
     * JQM node). This method allows to change this, and to put the request inside the queue but not run it when it reaches the top of the
     * queue. It will only be eligible for run when the given date is reached. When the given date is reached, standard queuing resumes.<br>
     * The resolution of this function is the minute: seconds and lower are ignored (truncated).<br>
     */
    public JobRequest setRunAfter(Calendar whenToRun);

    /**
     * <strong>Optional</strong><br>
     * This method allows to request for the run to be recurring. This actually creates a Scheduled Job for the given applicationName and
     * optionally queue and parameters. (all other JobRequest elements are ignored). Note that when using this, there is no request
     * immediately added to the queues - the actual requests will be created by the schedule.<br>
     * When creating a new recurrence, the ID returned by {@link JobRequest#enqueue()} is actually the schedule ID.
     */
    public JobRequest setRecurrence(String cronExpression);

    /**
     * <strong>Optional</strong><br>
     * The default behaviour for a newly submitted JobRequest is to run as soon as possible (i.e. as soon as there is a free slot inside a
     * JQM node). This method allows to change this, and to put the request inside the queue but not run it until the
     * {@link JqmClient#resumeJob(int)} method is called on the newly created job instance.
     */
    public JobRequest startHeld();

    /**
     * <strong>Optional</strong><br>
     * By default, JQM queues are pure FIFO. Setting a priority changes that: job instances with a higher priority always run before job
     * instances with a lower priority (or no priority at all). Also, this is directly translated into Thread priority when the job instance
     * actually runs.<br>
     * Higher priority is better (runs before the others, has more CPU share).<br>
     * Priority must be between {@link Thread#MIN_PRIORITY} and {@link Thread#MAX_PRIORITY}. To remove priority, set it to null.
     *
     * @param priority
     * @return
     */
    public JobRequest setPriority(Integer priority);
}
