package com.enioka.jqm.client.shared;

import java.util.List;

import com.enioka.jqm.client.api.JobInstance;
import com.enioka.jqm.client.api.JqmClientException;
import com.enioka.jqm.client.api.JqmInvalidRequestException;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 *
 * Used for callback factorisation between Query and JqmClient.
 */
public interface JqmClientQuerySubmitCallback
{
    /**
     * Generic query method. See {@link QueryBaseImpl} for arguments.
     *
     * @param query
     *            the query parameters.
     * @return the selected JobInstances
     * @throws JqmInvalidRequestException
     *             when query is null or inconsistent (e.g. trying to use pagination on a queue query)
     * @throws JqmClientException
     *             when an internal API implementation occurs. Usually linked to a configuration issue.
     */
    List<JobInstance> getJobs(QueryBaseImpl query);
}
