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

import java.io.InputStream;
import java.util.Calendar;
import java.util.List;

/**
 * The one and only interface for all JQM client operations. Implementations are NOT expected to be thread safe.
 *
 */
public interface JqmClient
{
    // /////////////////////////////////////////////////////////////////////
    // Enqueue functions
    // /////////////////////////////////////////////////////////////////////

    /**
     * Will create a new job instance inside an execution queue. All parameters (JQM parameters such as queue name, etc) as well as job
     * parameters) are given inside the job request argument <br>
     *
     * @param applicationName
     *            name of the job to launch
     * @param userName
     *            the user at the origin of the enqueue request (can be null or empty)
     * @return the ID of the job instance. Use this ID to track the job instance later on (it is a very common parameter inside the JQM
     *         client API)
     * @throws JqmInvalidRequestException
     *             when input data is invalid.
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    int enqueue(String applicationName, String userName);

    /**
     * Create a new job instance from another job instance that has successfully ended. This does not change the copied instance. Everything
     * is copied: parameters, queue, etc.
     *
     * @param jobIdToCopy
     *            the id of the job instance to copy
     * @return the id of the new instance
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist).
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    int enqueueFromHistory(int jobIdToCopy);

    /**
     * Entry point of the enqueue API. Creates a new empty JobRequest object, ready to run on this client. The returned JobRequest can
     * either be enqued by calling {@link JobRequest#enqueue()} or by giving it to {@link JqmClient#enqueue(JobRequest)} in any client
     * instance.
     *
     * @return a new empty JobRequest
     */
    JobRequest newJobRequest(String applicationName, String user);

    // /////////////////////////////////////////////////////////////////////
    // Job destruction
    // /////////////////////////////////////////////////////////////////////

    /**
     * Cancel a job, leaving a trace inside execution history. This is the normal way of canceling jobs - having traces of cancelled
     * requests is often important to draw statistics and educate users.<br>
     * This only works if the job instance is not already running: one cannot cancel a request that was already accepted. (throws exception
     * in that case)
     *
     * @param jobId
     *            the id of the job to cancel
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already run, job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void cancelJob(int jobId);

    /**
     * Remove an enqueued job from the queue, leaving no trace of it. This is an exceptional event - usually cancelJob would be used <br>
     * This only works if the job instance is not already running: one cannot cancel a request that was already accepted. (throws exception
     * in that case)
     *
     * @param jobId
     *            the id of the job to delete
     * @see #cancelJob(int) the more conventional way of removing job requests.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already done, job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void deleteJob(int jobId);

    /**
     * Kill a running job. Kill of a running job is not immediate, and is only possible when a job payload calls some JQM APIs. If none are
     * called, the job cannot be killed.<br>
     * If the job is still waiting in queue, this is equivalent to calling {@link JqmClient#cancelJob(int)}.
     *
     * @param jobId
     *            the id of the job to kill
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already done, job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void killJob(int jobId);

    /**
     * Remove a schedule. Effect is immediate.
     *
     * @param scheduleId
     *            the ID returned by the scheduling method (the ID of the scheduledJob object).
     * @throws JqmInvalidRequestException
     *             when input data is invalid (schedule does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void removeRecurrence(int scheduleId);

    // /////////////////////////////////////////////////////////////////////
    // Job Pause/restart
    // /////////////////////////////////////////////////////////////////////

    /**
     * Prevent a queued job request (not already accepted by an engine) from running. It can be resumed afterwards.
     *
     * @param jobId
     *            id of the job instance to pause
     * @see #resumeJob(int) resuming the paused request.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already running or run, job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void pauseQueuedJob(int jobId);

    /**
     * Resume a paused request and allow it to progress in queue once again.
     *
     * @param jobId
     *            id of the job instance to resume
     * @see #pauseQueuedJob(int) pause a job instance.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already run, job does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void resumeQueuedJob(int jobId);

    /**
     * @deprecated use {@link #resumeQueuedJob(int)} instead.
     * @param jobId
     */
    void resumeJob(int jobId);

    /**
     * Signal a running job instance that it should pause. The job instance may ignore this signal if it does not call any JobManager APIs.
     * Can be set only on job instance which are currently running.
     *
     * @param jobId
     *            id of the job instance to pause
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job instance is not running, job instance does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void pauseRunningJob(int jobId);

    /**
     * Signal a job instance which was paused during its run with {@link #pauseRunningJob(int)} that it is allowed to resume. This works
     * even if the pause signal was ignored by the job instance. Can be used only on job instance on which {@link #pauseRunningJob(int)} was
     * used.
     *
     * @param jobId
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job instance was never paused, job instance does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void resumeRunningJob(int jobId);

    /**
     * Will restart a crashed job. This will remove all trace of the failed execution.
     *
     * @param jobId
     *            id of the job instance that has failed.
     * @return the ID of the restarted job instance. Use this ID to track the job instance later on (it is a very common parameter inside
     *         the JQM client API). This is not the same ID as the one that has failed.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job not crashed, job does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    int restartCrashedJob(int jobId);

    // /////////////////////////////////////////////////////////////////////
    // Misc.
    // /////////////////////////////////////////////////////////////////////

    /**
     * Move a job instance from a queue to another queue
     *
     * @param jobId
     *            the job instance to modify
     * @param queueId
     *            id of the queue object to which the job should be affected
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist, queue does not exist...)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void setJobQueue(int jobId, int queueId);

    /**
     * Move a job instance from a queue to another queue
     *
     * @param jobId
     *            the job instance to modify
     * @param queue
     *            the queue object to which the job should be affected
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist, queue does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void setJobQueue(int jobId, Queue queue);

    /**
     * Change the position of a waiting job instance inside a queue.
     *
     * @param jobId
     *            id of the job instance to modify
     * @param newPosition
     *            its new position
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job already run, job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    void setJobQueuePosition(int jobId, int newPosition);

    /**
     * Change the priority of a queued (waiting) or running job instance.
     *
     * @param jobId
     *            id of the job instance to modify
     * @param priority
     *            must be between {@link Thread#MIN_PRIORITY} and {@link Thread#MAX_PRIORITY}. Higher priority is better (runs before the
     *            others, has more CPU share).
     */
    void setJobPriority(int jobId, int priority);

    /**
     * Change the "do not run before" date of a waiting job. Only works on job instances already having a "do not run before" date or
     * waiting in queue.
     *
     * @param jobId
     *            id of the job instance to modify
     * @param whenToRun
     *            the new date
     */
    void setJobRunAfter(int jobId, Calendar whenToRun);

    /**
     * Change the cron pattern used by a schedule. It is used on next schedule evaluation.
     *
     * @param scheduleId
     *            the schedule to update
     * @param cronExpression
     *            the new expression. Validity is not tested.
     */
    void setScheduleRecurrence(int scheduleId, String cronExpression);

    /**
     * Change the default queue of a scheduled job.
     *
     * @param scheduleId
     *            the schedule to update
     * @param queueId
     *            the new queue to use (see {@link #getQueues()}.
     */
    void setScheduleQueue(int scheduleId, int queueId);

    /**
     * Change the default priority for job instances created by this schedule.
     *
     * @param scheduleId
     *            the schedule to update
     * @param priority
     *            must be between {@link Thread#MIN_PRIORITY} and {@link Thread#MAX_PRIORITY}. Higher priority is better (runs before the
     *            others, has more CPU share).
     */
    void setSchedulePriority(int scheduleId, int priority);

    // /////////////////////////////////////////////////////////////////////
    // Job queries
    // /////////////////////////////////////////////////////////////////////

    /**
     * Retrieve a job instance. The returned object will contain all relevant data on that job instance such as its status. Call this
     * function again to refresh the data. <br>
     * This function queries both the active queues and the history. If not job is found, an exception is raised.
     *
     * @param jobId
     * @return the characteristics of the job instance.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    JobInstance getJob(int jobId);

    /**
     * Administrative method. List all currently running or waiting or finished job instances.
     *
     * @return the characteristics of the job instances.
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobInstance> getJobs();

    /**
     * Administrative method. List all currently running or waiting job instances.
     *
     * @return the characteristics of the job instances.
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobInstance> getActiveJobs();

    /**
     * List all currently running or waiting job instances for a given "user" (see userName parameter at enqueue time)
     *
     * @return the characteristics of the job instances.
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobInstance> getUserActiveJobs(String userName);

    /**
     * Entry point of the query API: creates a new Query object which can be used as a fluent API. The returned Query can then be directly
     * run against this client by using {@link Query#invoke()}.
     *
     * @return a new query ready to run on this client.
     */
    Query newQuery();

    // /////////////////////////////////////////////////////////////////////
    // Helpers to quickly access some job instance properties
    // /////////////////////////////////////////////////////////////////////

    /**
     * Get all messages that were created by a given job instance (running or done). Note that in addition to eventual messages created by
     * the payload itself, the JQM engine creates some messages so there should always be some for a completed job instance.
     *
     * @param jobId
     * @return all messages as strings
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<String> getJobMessages(int jobId);

    /**
     * Get the progress indication that may have been given by a job instance (running or done).
     *
     * @param jobId
     * @return the progress, or 0 if none was given.
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    int getJobProgress(int jobId);

    // /////////////////////////////////////////////////////////////////////
    // Deliverables retrieval
    // /////////////////////////////////////////////////////////////////////

    /**
     * Return all metadata concerning the (potential) files created by the job instance.
     *
     * @param jobId
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<Deliverable> getJobDeliverables(int jobId);

    /**
     * Return all files created by a job instance if any. The stream is not open: opening and closing it is the caller's responsibility.<br>
     * <strong>The underlying temporary files are deleted at stream closure</strong>.<br>
     * <strong>In some implementations, this client method may require a direct TCP connection to the engine that has run the instance. In
     * all implementations, the engine that has run the instance must be up.</strong>
     *
     * @param jobId
     * @return a list of streams
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<InputStream> getJobDeliverablesContent(int jobId);

    /**
     * Return one file created by a job instance. The stream is not open: opening and closing it is the caller's responsibility.<br>
     * <strong>The underlying temporary files are deleted at stream closure</strong>. <br>
     * <strong>In some implementations, this client method may require a direct TCP connection to the engine that has run the instance. In
     * all implementations, the engine that has run the instance must be up.</strong>
     *
     * @param file
     *            the file to retrieve (usually obtained through {@link #getJobDeliverables(int)})
     * @return a stream
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    InputStream getDeliverableContent(Deliverable file);

    /**
     * Return one file created by a job instance. The stream is not open: opening and closing it is the caller's responsibility.<br>
     * <strong>The underlying temporary files are deleted at stream closure</strong>. <br>
     * <strong>In some implementations, this client method may require a direct TCP connection to the engine that has run the instance. In
     * all implementations, the engine that has run the instance must be up.</strong>
     *
     * @param fileId
     *            the id of the file to retrieve (usually obtained through {@link #getJobDeliverables(int)})
     * @return a stream
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    InputStream getDeliverableContent(int fileId);

    /**
     * Returns the standard output flow of of an ended job instance <br>
     * <strong>In some implementations, this client method may require a direct TCP connection to the engine that has run the instance. In
     * all implementations, the engine that has run the instance must be up.</strong>
     *
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job instance does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     * @see #getJobLogStdErr(int)
     */
    InputStream getJobLogStdOut(int jobId);

    /**
     * Returns the standard error flow of of an ended job instance<br>
     * <strong>In some implementations, this client method may require a direct TCP connection to the engine that has run the instance. In
     * all implementations, the engine that has run the instance must be up.</strong>
     *
     * @throws JqmInvalidRequestException
     *             when input data is invalid (job does not exist)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     * @see #getJobLogStdOut(int)
     */
    InputStream getJobLogStdErr(int jobId);

    // /////////////////////////////////////////////////////////////////////
    // Queue API
    // /////////////////////////////////////////////////////////////////////

    /**
     * List all available queues with their characteristics. Useful mostly for admin operations and changing a job instance from one queue
     * to another.
     *
     * @return a list of queues
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<Queue> getQueues();

    /**
     * All subscribers to the given queue pause until {@link #resumeQueue(Queue)} is called (can also be reactivated from the UI by an
     * administrator).
     *
     * @param q
     *            the queue to pause
     */
    void pauseQueue(Queue q);

    /**
     * Resume all subscribers to the given queue. Idempotent.
     *
     * @param q
     *            the queue to resume.
     */
    void resumeQueue(Queue q);

    /**
     * All job instances waiting inside this queue are purged. Does not affect job instances already running.
     *
     * @param q
     *            the queue to clear.
     */
    void clearQueue(Queue q);

    /**
     * Query the status of a given queue
     *
     * @param q
     *            the queue to query
     * @return the status at the time of call.
     */
    QueueStatus getQueueStatus(Queue q);

    /**
     * Query capacity.
     *
     * @param q
     *            the queue to query
     * @return sum of maximum parallel instances for the queue around the active nodes
     */
    int getQueueEnabledCapacity(Queue q);

    // /////////////////////////////////////////////////////////////////////
    // Parameters retrieval
    // /////////////////////////////////////////////////////////////////////

    /**
     * Lists all the available {@link JobDef} objects, i.e. the different payloads that can be launched by JQM.
     *
     * @return a list of JobDef
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobDef> getJobDefinitions();

    /**
     * Lists all the available {@link JobDef} objects for a given application, i.e. the different payloads that can be launched by JQM. The
     * "application" is the optional tag that can be given inside the <code> &ltapplication&gt</code> tag of the JobDef XML file.<br>
     * If application is null, this method is equivalent to {@link #getJobDefinitions()}.
     *
     * @return a list of JobDef
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobDef> getJobDefinitions(String application);

    /**
     * Gets a {@link JobDef} from its {@link JobDef#getApplicationName()}. Throws if the job definition does not exists.
     */
    JobDef getJobDefinition(String name);

    // /////////////////////////////////////////////////////////////////////
    // Technical
    // /////////////////////////////////////////////////////////////////////

    /**
     * Free resources. Client is unusable after calling this method. What is freed depends on the implementation, it may be nothing at all.
     * This method should never throw any exception.
     */
    void dispose();
}
