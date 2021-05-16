package com.enioka.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import com.enioka.api.admin.JndiObjectResourceDto;
import com.enioka.api.admin.JobDefDto;
import com.enioka.api.admin.NodeDto;
import com.enioka.api.admin.QueueDto;
import com.enioka.api.admin.QueueMappingDto;
import com.enioka.api.admin.RRoleDto;
import com.enioka.api.admin.RUserDto;
import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.jdbc.NonUniqueResultException;
import com.enioka.jqm.jdbc.QueryResult;
import com.enioka.jqm.model.DeploymentParameter;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JndiObjectResource;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.JobDef.PathType;
import com.enioka.jqm.model.JobDefParameter;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.model.Queue;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.ScheduledJob;

import org.apache.shiro.crypto.SecureRandomNumberGenerator;
import org.apache.shiro.crypto.hash.Sha512Hash;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set of methods to handle metadata.
 */
public class MetaService
{
    private static Logger jqmlogger = LoggerFactory.getLogger(MetaService.class);

    ///////////////////////////////////////////////////////////////////////////
    // GLOBAL DELETE
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Empty the database. <br>
     * No commit performed.
     *
     * @param cnx
     *                  database session to use. Not committed.
     * @param force
     *                  set to true if you want to delete metadata even if there is still transactional data depending on it.
     */
    public static void deleteAllMeta(DbConn cnx, boolean force)
    {
        if (force)
        {
            deleteAllTransac(cnx);
        }

        cnx.runUpdate("globalprm_delete_all");
        cnx.runUpdate("dp_delete_all");
        cnx.runUpdate("sjprm_delete_all");
        cnx.runUpdate("sj_delete_all");
        cnx.runUpdate("jdprm_delete_all");
        cnx.runUpdate("node_delete_all");
        cnx.runUpdate("jd_delete_all");
        cnx.runUpdate("q_delete_all");
        cnx.runUpdate("jndiprm_delete_all");
        cnx.runUpdate("jndi_delete_all");
        cnx.runUpdate("pki_delete_all"); // No corresponding DTO.
        cnx.runUpdate("perm_delete_all");
        cnx.runUpdate("role_delete_all");
        cnx.runUpdate("user_delete_all");
    }

    /**
     * This method is an exception - it does not deal with metadata but transactional data. It is included here to allow easier purge of
     * metadata.<br>
     * No commit performed.
     *
     * @param cnx
     */
    public static void deleteAllTransac(DbConn cnx)
    {
        cnx.runUpdate("deliverable_delete_all");
        cnx.runUpdate("message_delete_all");
        cnx.runUpdate("history_delete_all");
        cnx.runUpdate("jiprm_delete_all");
        cnx.runUpdate("ji_delete_all");
    }

    ///////////////////////////////////////////////////////////////////////////
    // GLOBAL PARAMETER
    ///////////////////////////////////////////////////////////////////////////


    public static void syncGlobalParameters(DbConn cnx, List<GlobalParameter> dtos)
    {
        for (GlobalParameter existing : GlobalParameter.selectAll(cnx))
        {
            boolean foundInNewSet = false;
            for (GlobalParameter newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                GlobalParameter.deleteGlobalParameter(cnx, existing.getId());
            }
        }

        for (GlobalParameter dto : dtos)
        {
            dto.upsert(cnx);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // JNDI
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteJndiObjectResource(DbConn cnx, int id)
    {
        cnx.runUpdate("jndiprm_delete_for_resource", id);
        QueryResult qr = cnx.runUpdate("jndi_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    /**
     * Update or insert a resource. Convention is that if dto.id is null, we always insert. If non null, always update. (so we use the
     * technical key, not the functional key - because the functional key can itself be updated!<br>
     * <br>
     * This method only updates (and sets the timestamp for last update) if there are actual modifications done. Modifications are detected
     * by value comparison on all fields (except the ID, but including parameters).
     *
     * @param cnx
     * @param dto
     */
    public static void upsertJndiObjectResource(DbConn cnx, JndiObjectResourceDto dto)
    {
        if (dto.getId() != null)
        {
            QueryResult qr = cnx.runUpdate("jndi_update_changed_by_id", dto.getAuth(), dto.getDescription(), dto.getFactory(),
                    dto.getName(), dto.getSingleton(), dto.getTemplate(), dto.getType(), dto.getId(), dto.getAuth(), dto.getDescription(),
                    dto.getFactory(), dto.getName(), dto.getSingleton(), dto.getTemplate(), dto.getType());
            if (qr.nbUpdated != 1)
            {
                jqmlogger.debug("No update was done as object either does not exist or no modifications were done");
            }

            // Sync parameters too
            List<String> existingKeys = cnx.runSelectColumn("jndiprm_select_all_in_jndisrc", 2, String.class, dto.getId());
            for (Map.Entry<String, String> e : dto.getParameters().entrySet())
            {
                if (existingKeys.contains(e.getKey()))
                {
                    // Update
                    cnx.runUpdate("jndiprm_update_changed_by_id", e.getValue(), dto.getId(), e.getKey(), e.getValue());
                }
                else
                {
                    // Insert
                    cnx.runUpdate("jndiprm_insert", e.getKey(), e.getValue(), dto.getId());
                }
            }
            ResultSet rs = cnx.runSelect("jndiprm_select_all_in_jndisrc", dto.getId());
            try
            {
                while (rs.next())
                {
                    String key = rs.getString(2);
                    int id = rs.getInt(1);

                    if (!dto.getParameters().containsKey(key))
                    {
                        cnx.runUpdate("jndiprm_delete_by_id", id);
                    }
                }
                rs.close();
            }
            catch (Exception e)
            {
                throw new JqmAdminApiInternalException(e);
            }
        }
        else
        {
            JndiObjectResource.create(cnx, dto.getName(), dto.getType(), dto.getFactory(), dto.getDescription(), dto.getSingleton(),
                    dto.getParameters());
        }
    }

    private static JndiObjectResourceDto mapJndiObjectResource(ResultSet rs) throws SQLException
    {
        JndiObjectResourceDto res = new JndiObjectResourceDto();
        res.setId(rs.getInt(1));
        res.setName(rs.getString(2));
        res.setAuth(rs.getString(3));
        res.setType(rs.getString(4));
        res.setFactory(rs.getString(5));
        res.setDescription(rs.getString(6));
        res.setTemplate(rs.getString(7));
        res.setSingleton(rs.getBoolean(8));

        return res;
    }

    private static JndiObjectResourceDto getUniqueJndiResource(DbConn cnx, String query_key, Object key)
    {
        List<JndiObjectResourceDto> res = getJndiObjectResources(cnx, query_key, key);
        if (res.isEmpty())
        {
            throw new NoResultException("The query returned zero rows when one was expected.");
        }
        if (res.size() > 1)
        {
            throw new NonUniqueResultException("The query returned more than one row when one was expected");
        }

        return res.get(0);
    }

    public static JndiObjectResourceDto getJndiObjectResource(DbConn cnx, String key)
    {
        return getUniqueJndiResource(cnx, "jndi_select_by_key", key);
    }

    public static JndiObjectResourceDto getJndiObjectResource(DbConn cnx, Integer key)
    {
        return getUniqueJndiResource(cnx, "jndi_select_by_id", key);
    }

    public static List<JndiObjectResourceDto> getJndiObjectResource(DbConn cnx)
    {
        return getJndiObjectResources(cnx, "jndi_select_all");
    }

    private static List<JndiObjectResourceDto> getJndiObjectResources(DbConn cnx, String query_key, Object... prms)
    {
        ResultSet rs = cnx.runSelect(query_key, prms);
        List<JndiObjectResourceDto> res = new ArrayList<>();
        List<Integer> rscIds = new ArrayList<>();
        try
        {
            // The resources
            while (rs.next())
            {
                JndiObjectResourceDto tmp = mapJndiObjectResource(rs);
                rscIds.add(tmp.getId());
                res.add(tmp);
            }
            rs.close();

            // The parameters
            if (!rscIds.isEmpty())
            {
                rs = cnx.runSelect("jndiprm_select_all_in_jndisrc_list", rscIds);
                while (rs.next())
                {
                    String key = rs.getString(2);
                    String value = rs.getString(4);
                    int rid = rs.getInt(5);

                    for (JndiObjectResourceDto tmp : res)
                    {
                        if (tmp.getId().equals(rid))
                        {
                            tmp.addParameter(key, value);
                            break;
                        }
                    }
                }
                rs.close();
            }
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException("An error occurred while querying the database", e);
        }
        return res;
    }

    public static void syncJndiObjectResource(DbConn cnx, List<JndiObjectResourceDto> dtos)
    {
        for (JndiObjectResourceDto existing : getJndiObjectResource(cnx))
        {
            boolean foundInNewSet = false;
            for (JndiObjectResourceDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteJndiObjectResource(cnx, existing.getId());
            }
        }

        for (JndiObjectResourceDto dto : dtos)
        {
            upsertJndiObjectResource(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // JOB DEF
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteJobDef(DbConn cnx, int id)
    {
        int countRunning = cnx.runSelectSingle("ji_select_count_by_jd", Integer.class, id);
        if (countRunning > 0)
        {
            throw new JqmAdminApiUserException(
                    "cannot delete a job definition with running instances. Disable it, wait for the end of all running instances, then retry.");
        }

        cnx.runUpdate("jdprm_delete_all_for_jd", id);
        QueryResult qr = cnx.runUpdate("jd_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    private static JobDefDto mapJobDef(ResultSet rs, int colShift)
    {
        JobDefDto tmp = new JobDefDto();
        try
        {
            tmp.setId(rs.getInt(1 + colShift));
            tmp.setApplication(rs.getString(2 + colShift));
            tmp.setApplicationName(rs.getString(3 + colShift));
            tmp.setClassLoaderId(rs.getInt(4 + colShift) > 0 ? rs.getInt(4 + colShift) : null);
            tmp.setCanBeRestarted(true);
            tmp.setDescription(rs.getString(5 + colShift));
            tmp.setEnabled(rs.getBoolean(6 + colShift));
            // tmp. = rs.getBoolean(7 + colShift); // TODO: external exposure?
            tmp.setHighlander(rs.getBoolean(8 + colShift));
            tmp.setJarPath(rs.getString(9 + colShift));
            tmp.setPathType(rs.getString(17 + colShift));
            tmp.setJavaClassName(rs.getString(10 + colShift));
            // tmp.javaOpts = rs.getString(11 + colShift);
            tmp.setKeyword1(rs.getString(12 + colShift));
            tmp.setKeyword2(rs.getString(13 + colShift));
            tmp.setKeyword3(rs.getString(14 + colShift));
            tmp.setReasonableRuntimeLimitMinute(rs.getInt(15 + colShift));
            tmp.setModule(rs.getString(16 + colShift));
            tmp.setQueueId(rs.getInt(18 + colShift) > 0 ? rs.getInt(18 + colShift) : null);
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException(e);
        }
        return tmp;
    }

    private static void addSubElementsToDto(DbConn cnx, List<JobDefDto> dtos)
    {
        List<Integer> currentIdList = null;
        List<List<Integer>> allIdLists = new ArrayList<>();
        for (JobDefDto dto : dtos)
        {
            if (currentIdList == null || currentIdList.size() == 500)
            {
                currentIdList = new ArrayList<>();
                allIdLists.add(currentIdList);
            }
            currentIdList.add(dto.getId());
        }

        // Parameters
        try
        {
            for (List<Integer> ids : allIdLists)
            {
                ResultSet rs = cnx.runSelect("jdprm_select_all_for_jd_list", ids);
                while (rs.next())
                {
                    String key = rs.getString(2);
                    String value = rs.getString(3);
                    int id = rs.getInt(4);

                    for (JobDefDto dto : dtos)
                    {
                        if (dto.getId().equals(id))
                        {
                            dto.getParameters().put(key, value);
                            break;
                        }
                    }
                }
                rs.close();
            }
        }
        catch (Exception e)
        {
            throw new JqmAdminApiInternalException(e);
        }

        // Schedules
        try
        {
            for (List<Integer> ids : allIdLists)
            {
                List<ScheduledJob> sjs = ScheduledJob.select(cnx, "sj_select_for_jd_list", ids);

                for (ScheduledJob sj : sjs)
                {
                    com.enioka.api.admin.ScheduledJob sjdto = new com.enioka.api.admin.ScheduledJob();
                    sjdto.setCronExpression(sj.getCronExpression());
                    sjdto.setLastUpdated(sj.getLastUpdated());
                    sjdto.setParameters(sj.getParameters());
                    sjdto.setQueue(sj.getQueue());
                    sjdto.setPriority(sj.getPriority());
                    sjdto.setId(sj.getId());

                    for (JobDefDto dto : dtos)
                    {
                        if (dto.getId().equals(sj.getJobDefinition()))
                        {
                            dto.getSchedules().add(sjdto);
                            break;
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            throw new JqmAdminApiInternalException(e);
        }
    }

    public static List<JobDefDto> getJobDef(DbConn cnx)
    {
        List<JobDefDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect("jd_select_all");
            while (rs.next())
            {
                res.add(mapJobDef(rs, 0));
            }
            rs.close();

            addSubElementsToDto(cnx, res);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static JobDefDto getJobDef(DbConn cnx, Integer id)
    {
        ResultSet rs = null;
        try
        {
            rs = cnx.runSelect("jd_select_by_id", id);
            if (!rs.next())
            {
                throw new JqmAdminApiUserException("no result");
            }

            JobDefDto tmp = mapJobDef(rs, 0);
            List<JobDefDto> tmp2 = new ArrayList<>();
            tmp2.add(tmp);
            addSubElementsToDto(cnx, tmp2);
            return tmp;
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static void upsertJobDef(DbConn cnx, JobDefDto dto)
    {
        if (dto.getId() != null)
        {
            // Job: do it in a brutal way (no date to update here).
            cnx.runUpdate("jd_update_all_fields_by_id", dto.getApplication(), dto.getApplicationName(), dto.getDescription(),
                    dto.isEnabled(), false, dto.isHighlander(), dto.getJarPath(), dto.getJavaClassName(), null, dto.getKeyword1(),
                    dto.getKeyword2(), dto.getKeyword3(), dto.getReasonableRuntimeLimitMinute(), dto.getModule(),
                    PathType.valueOf(dto.getPathType()), dto.getClassLoaderId(), dto.getQueueId(), dto.getId());

            // Parameter sync is trivial for now: delete and recreate.
            cnx.runUpdate("jdprm_delete_all_for_jd", dto.getId());
            for (Map.Entry<String, String> e : dto.getParameters().entrySet())
            {
                JobDefParameter.create(cnx, e.getKey(), e.getValue(), dto.getId());
            }

            // Schedule sync cannot be that simple, as we must follow their updates in the scheduler (with key = PK).
            if (dto.getSchedules().size() == 0)
            {
                cnx.runUpdate("sjprm_delete_all_for_jd", dto.getId());
                cnx.runUpdate("sj_delete_all_for_jd", dto.getId());
            }
            else
            {
                List<ScheduledJob> existingSchedules = ScheduledJob.select(cnx, "sj_select_for_jd", dto.getId());
                List<ScheduledJob> toDelete = new ArrayList<>();

                // First remove SJ not present anymore in the DTO.
                firstloop: for (ScheduledJob sj : existingSchedules)
                {
                    for (com.enioka.api.admin.ScheduledJob inDto : dto.getSchedules())
                    {
                        if (inDto.getId() == null || inDto.getId().equals(sj.getId()))
                        {
                            continue firstloop;
                        }
                    }
                    toDelete.add(sj);
                    cnx.runUpdate("sjprm_delete_all_for_sj", sj.getId());
                    cnx.runUpdate("sj_delete_by_id", sj.getId());
                }

                // Then update or insert remaining SJ.
                // First, remove all parameters...
                for (com.enioka.api.admin.ScheduledJob sj : dto.getSchedules())
                {
                    if (sj.getId() == null)
                    {
                        ScheduledJob.create(cnx, sj.getCronExpression(), dto.getId(), sj.getQueue(), sj.getPriority(), sj.getParameters());
                    }
                    else
                    {
                        // This is an update. Get the existing SJ from the database.
                        boolean update = false;
                        ScheduledJob existing = null;
                        for (ScheduledJob sj2 : existingSchedules)
                        {
                            if (sj2.getId() == sj.getId())
                            {
                                existing = sj2;
                                break;
                            }
                        }
                        if (existing == null)
                        {
                            throw new JqmAdminApiUserException("Trying to update a sccheduled job which does not exist - id " + sj.getId());
                        }

                        // Parameter update?
                        if (!existing.getParameters().equals(sj.getParameters()))
                        {
                            update = true;
                            cnx.runUpdate("sjprm_delete_all_for_sj", existing.getId());
                            for (Map.Entry<String, String> e : sj.getParameters().entrySet())
                            {
                                cnx.runUpdate("sjprm_insert", e.getKey(), e.getValue(), sj.getId());
                            }
                        }

                        // Update main SJ fields (only if needed).
                        if (update || !sj.getCronExpression().equals(existing.getCronExpression()) || sj.getQueue() != existing.getQueue())
                        {
                            cnx.runUpdate("sj_update_all_fields_by_id", sj.getCronExpression(), sj.getQueue(), sj.getPriority(),
                                    sj.getId());
                        }
                    }
                }
            }

        }
        else
        {
            int i = JobDef.create(cnx, dto.getDescription(), dto.getJavaClassName(), dto.getParameters(), dto.getJarPath(),
                    dto.getQueueId(), dto.getReasonableRuntimeLimitMinute(), dto.getApplicationName(), dto.getApplication(),
                    dto.getModule(), dto.getKeyword1(), dto.getKeyword2(), dto.getKeyword3(), dto.isHighlander(), dto.getClassLoaderId(),
                    PathType.valueOf(dto.getPathType()));

            // Sync the schedules too.
            for (com.enioka.api.admin.ScheduledJob sjdto : dto.getSchedules())
            {
                ScheduledJob.create(cnx, sjdto.getCronExpression(), i, sjdto.getQueue(), sjdto.getPriority(), sjdto.getParameters());
            }
        }
    }

    public static void syncJobDefs(DbConn cnx, List<JobDefDto> dtos)
    {
        for (JobDefDto existing : getJobDef(cnx))
        {
            boolean foundInNewSet = false;
            for (JobDefDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteJobDef(cnx, existing.getId());
            }
        }

        for (JobDefDto dto : dtos)
        {
            upsertJobDef(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // NODE
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteNode(DbConn cnx, int id)
    {
        int countRunning = cnx.runSelectSingle("ji_select_count_by_node", Integer.class, id);
        if (countRunning > 0)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException(
                    "cannot delete a node with running instances. Disable it, wait for the end of all running instances, then retry.");
        }

        try
        {
            Node n = Node.select_single(cnx, "node_select_by_id", id);
            Calendar limit = Calendar.getInstance();
            limit.add(Calendar.MINUTE, -10);
            if (n.getLastSeenAlive() != null && n.getLastSeenAlive().after(limit))
            {
                cnx.setRollbackOnly();
                throw new JqmAdminApiUserException(
                        "Can only remove a node either properly shut down or that has crashed more than 10 minutes ago.");
            }
        }
        catch (NoResultException e)
        {
            throw new JqmAdminApiUserException("no item with ID " + id);
        }

        cnx.runUpdate("dp_delete_for_node", id);
        QueryResult qr = cnx.runUpdate("node_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    static NodeDto mapNode(ResultSet rs, int colShift)
    {
        try
        {
            NodeDto tmp = new NodeDto();

            tmp.setId(rs.getInt(1 + colShift));

            tmp.setOutputDirectory(rs.getString(2 + colShift));
            tmp.setDns(rs.getString(3 + colShift));
            tmp.setEnabled(rs.getBoolean(4 + colShift));
            tmp.setJmxRegistryPort(rs.getInt(5 + colShift));
            tmp.setJmxServerPort(rs.getInt(6 + colShift));
            tmp.setLoadApiAdmin(rs.getBoolean(7 + colShift));
            tmp.setLoadApiClient(rs.getBoolean(8 + colShift));
            tmp.setLoapApiSimple(rs.getBoolean(9 + colShift));
            tmp.setName(rs.getString(10 + colShift));
            tmp.setPort(rs.getInt(11 + colShift));
            tmp.setJobRepoDirectory(rs.getString(12 + colShift));
            tmp.setRootLogLevel(rs.getString(13 + colShift));
            tmp.setStop(rs.getBoolean(14 + colShift));
            tmp.setTmpDirectory(rs.getString(15 + colShift));

            Calendar c = null;
            if (rs.getTimestamp(16 + colShift) != null)
            {
                c = Calendar.getInstance();
                c.setTimeInMillis(rs.getTimestamp(16 + colShift).getTime());
            }
            tmp.setLastSeenAlive(c);

            return tmp;
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static List<NodeDto> getNodes(DbConn cnx)
    {
        List<NodeDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect("node_select_all");
            while (rs.next())
            {
                res.add(mapNode(rs, 0));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static NodeDto getNode(DbConn cnx, int id)
    {
        ResultSet rs = null;
        try
        {
            rs = cnx.runSelect("node_select_by_id", id);
            if (!rs.next())
            {
                throw new JqmAdminApiUserException("no result");
            }

            return mapNode(rs, 0);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static NodeDto getNode(DbConn cnx, String nodeName)
    {
        ResultSet rs = null;
        try
        {
            rs = cnx.runSelect("node_select_by_key", nodeName);
            if (!rs.next())
            {
                throw new JqmAdminApiUserException("no result");
            }

            return mapNode(rs, 0);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static void upsertNode(DbConn cnx, NodeDto dto)
    {
        if (dto.getId() != null)
        {
            QueryResult qr = cnx.runUpdate("node_update_changed_by_id", dto.getOutputDirectory(), dto.getDns(), dto.getEnabled(),
                    dto.getJmxRegistryPort(), dto.getJmxServerPort(), dto.getLoadApiAdmin(), dto.getLoadApiClient(), dto.getLoapApiSimple(),
                    dto.getName(), dto.getPort(), dto.getJobRepoDirectory(), dto.getRootLogLevel(), dto.getStop(), dto.getTmpDirectory(),
                    dto.getId(), dto.getOutputDirectory(), dto.getDns(), dto.getEnabled(), dto.getJmxRegistryPort(), dto.getJmxServerPort(),
                    dto.getLoadApiAdmin(), dto.getLoadApiClient(), dto.getLoapApiSimple(), dto.getName(), dto.getPort(),
                    dto.getJobRepoDirectory(), dto.getRootLogLevel(), dto.getStop(), dto.getTmpDirectory());

            if (qr.nbUpdated != 1)
            {
                jqmlogger.debug("No update was done as object either does not exist or no modifications were done");
            }
        }
        else
        {
            // Should actually never be used... nodes should be created through CLI.
            Node.create(cnx, dto.getName(), dto.getPort(), dto.getOutputDirectory(), dto.getJobRepoDirectory(), dto.getTmpDirectory(),
                    dto.getDns(), dto.getRootLogLevel());
        }
    }

    public static void syncNodes(DbConn cnx, List<NodeDto> dtos)
    {
        for (NodeDto existing : getNodes(cnx))
        {
            boolean foundInNewSet = false;
            for (NodeDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteQueue(cnx, existing.getId());
            }
        }

        for (NodeDto dto : dtos)
        {
            upsertNode(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // QUEUE
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteQueue(DbConn cnx, int id)
    {
        int countRunning = cnx.runSelectSingle("ji_select_count_by_queue", Integer.class, id);
        if (countRunning > 0)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException(
                    "cannot delete a queue with running instances. Disable it, wait for the end of all running instances, then retry.");
        }

        cnx.runUpdate("dp_delete_for_queue", id);
        QueryResult qr = cnx.runUpdate("q_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    private static QueueDto mapQueue(ResultSet rs, int colShift)
    {
        try
        {
            QueueDto tmp = new QueueDto();

            tmp.setId(rs.getInt(1 + colShift));
            tmp.setDefaultQueue(rs.getBoolean(2 + colShift));
            tmp.setDescription(rs.getString(3 + colShift));
            tmp.setName(rs.getString(4 + colShift));

            return tmp;
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException(e);
        }
    }

    public static List<QueueDto> getQueues(DbConn cnx)
    {
        List<QueueDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect("q_select_all");
            while (rs.next())
            {
                res.add(mapQueue(rs, 0));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static QueueDto getQueue(DbConn cnx, int id)
    {
        ResultSet rs = null;
        try
        {
            rs = cnx.runSelect("q_select_by_id", id);
            if (!rs.next())
            {
                throw new JqmAdminApiUserException("no result");
            }

            return mapQueue(rs, 0);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static void upsertQueue(DbConn cnx, QueueDto dto)
    {
        if (dto.getId() != null)
        {
            cnx.runUpdate("q_update_changed_by_id", dto.isDefaultQueue(), dto.getDescription(), dto.getName(), dto.getId(),
                    dto.isDefaultQueue(), dto.getDescription(), dto.getName());
        }
        else
        {
            Queue.create(cnx, dto.getName(), dto.getDescription(), dto.isDefaultQueue());
        }
    }

    public static void syncQueues(DbConn cnx, List<QueueDto> dtos)
    {
        for (QueueDto existing : getQueues(cnx))
        {
            boolean foundInNewSet = false;
            for (QueueDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteQueue(cnx, existing.getId());
            }
        }

        for (QueueDto dto : dtos)
        {
            upsertQueue(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // DEPLOYMENT PARAMETER
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteQueueMapping(DbConn cnx, int id)
    {
        QueryResult qr = cnx.runUpdate("dp_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    private static QueueMappingDto mapQueueMapping(ResultSet rs, int colShift)
    {
        try
        {
            QueueMappingDto tmp = new QueueMappingDto();

            tmp.setId(rs.getInt(1 + colShift));
            tmp.setEnabled(rs.getBoolean(2 + colShift));

            tmp.setNbThread(rs.getInt(4 + colShift));
            tmp.setPollingInterval(rs.getInt(5 + colShift));
            tmp.setNodeId(rs.getInt(6 + colShift));
            tmp.setQueueId(rs.getInt(7 + colShift));
            tmp.setNodeName(rs.getString(8 + colShift));
            tmp.setQueueName(rs.getString(9 + colShift));

            return tmp;
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException(e);
        }
    }

    public static List<QueueMappingDto> getQueueMappings(DbConn cnx)
    {
        List<QueueMappingDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect("dp_select_all_with_names");
            while (rs.next())
            {
                res.add(mapQueueMapping(rs, 0));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static QueueMappingDto getQueueMapping(DbConn cnx, int id)
    {
        ResultSet rs = null;
        try
        {
            rs = cnx.runSelect("dp_select_with_names_by_id", id);
            if (!rs.next())
            {
                throw new JqmAdminApiUserException("no result");
            }
            rs.close();
            return mapQueueMapping(rs, 0);
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
    }

    public static List<QueueMappingDto> getNodeQueueMappings(DbConn cnx, int nodeId)
    {
        ResultSet rs = null;
        List<QueueMappingDto> res = new ArrayList<>();
        try
        {
            rs = cnx.runSelect("dp_select_with_names_by_node_id", nodeId);
            while (rs.next())
            {
                res.add(mapQueueMapping(rs, 0));
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static void upsertQueueMapping(DbConn cnx, QueueMappingDto dto)
    {
        if (dto.getId() != null)
        {
            cnx.runUpdate("dp_update_changed_by_id", dto.getEnabled(), dto.getNbThread(), dto.getPollingInterval(), dto.getNodeId(),
                    dto.getQueueId(), dto.getId(), dto.getEnabled(), dto.getNbThread(), dto.getPollingInterval(), dto.getNodeId(),
                    dto.getQueueId());
        }
        else
        {
            DeploymentParameter.create(cnx, dto.getNodeId(), dto.getNbThread(), dto.getPollingInterval(), dto.getQueueId());
        }
    }

    public static void syncQueueMappings(DbConn cnx, List<QueueMappingDto> dtos)
    {
        for (QueueMappingDto existing : getQueueMappings(cnx))
        {
            boolean foundInNewSet = false;
            for (QueueMappingDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteQueueMapping(cnx, existing.getId());
            }
        }

        for (QueueMappingDto dto : dtos)
        {
            upsertQueueMapping(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // ROLE
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteRole(DbConn cnx, int id, boolean force)
    {
        if (force)
        {
            cnx.runUpdate("user_remove_role", id);
        }
        else
        {
            int userUsingRole = cnx.runSelectSingle("user_select_count_using_role", Integer.class, id);
            if (userUsingRole > 0)
            {
                cnx.setRollbackOnly();
                throw new JqmAdminApiUserException(
                        "cannot delete a role currently attributed to a user. Remove role attribution of use force parameter.");
            }
        }

        QueryResult qr = cnx.runUpdate("role_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    private static RRoleDto mapRole(ResultSet rs, int colShift)
    {
        try
        {
            RRoleDto tmp = new RRoleDto();

            tmp.setId(rs.getInt(1 + colShift));
            tmp.setName(rs.getString(2 + colShift));
            tmp.setDescription(rs.getString(3 + colShift));

            return tmp;
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException(e);
        }
    }

    private static List<RRoleDto> getRoles(DbConn cnx, String query_key, int colShift, Object... args)
    {
        List<RRoleDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect(query_key, args);
            RRoleDto tmp = null;
            while (rs.next())
            {
                tmp = mapRole(rs, colShift);
                res.add(tmp);
            }
            rs.close();

            List<Integer> ids = new ArrayList<>();
            for (RRoleDto dto : res)
            {
                ids.add(dto.getId());
            }

            rs = cnx.runSelect("perm_select_all_in_role_list", ids);
            while (rs.next())
            {
                int role_id = rs.getInt(3);
                String permName = rs.getString(2);

                for (RRoleDto dto : res)
                {
                    if (dto.getId().equals(role_id))
                    {
                        dto.addPermission(permName);
                    }
                }
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static List<RRoleDto> getRoles(DbConn cnx)
    {
        return getRoles(cnx, "role_select_all", 0);
    }

    public static RRoleDto getRole(DbConn cnx, int id)
    {
        List<RRoleDto> res = getRoles(cnx, "role_select_by_id", 0, id);
        if (res.size() == 1)
        {
            return res.get(0);
        }
        else
        {
            throw new JqmAdminApiUserException("no result");
        }
    }

    public static void upsertRole(DbConn cnx, RRoleDto dto)
    {
        if (dto.getId() != null)
        {
            cnx.runUpdate("role_update_all_by_id", dto.getName(), dto.getDescription(), dto.getId());

            // Permissions
            cnx.runUpdate("perm_delete_for_role", dto.getId());
            for (String i : dto.getPermissions())
            {
                cnx.runUpdate("perm_insert", i, dto.getId());
            }
        }
        else
        {
            RRole.create(cnx, dto.getName(), dto.getDescription(), dto.getPermissions().toArray(new String[dto.getPermissions().size()]));
        }
    }

    public static void syncRoles(DbConn cnx, List<RRoleDto> dtos)
    {
        for (RRoleDto existing : getRoles(cnx))
        {
            boolean foundInNewSet = false;
            for (RRoleDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteRole(cnx, existing.getId(), false);
            }
        }

        for (RRoleDto dto : dtos)
        {
            upsertRole(cnx, dto);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // USER
    ///////////////////////////////////////////////////////////////////////////

    public static void deleteUser(DbConn cnx, int id)
    {
        QueryResult qr = cnx.runUpdate("user_delete_by_id", id);
        if (qr.nbUpdated != 1)
        {
            cnx.setRollbackOnly();
            throw new JqmAdminApiUserException("no item with ID " + id);
        }
    }

    private static RUserDto mapUser(ResultSet rs, int colShift, DbConn cnx)
    {
        try
        {
            RUserDto tmp = new RUserDto();

            tmp.setId(rs.getInt(1 + colShift));
            tmp.setLogin(rs.getString(2 + colShift));

            tmp.setLocked(rs.getBoolean(5 + colShift));
            tmp.setExpirationDate(cnx.getCal(rs, 6 + colShift));
            tmp.setCreationDate(cnx.getCal(rs, 7 + colShift));

            tmp.setEmail(rs.getString(9 + colShift));
            tmp.setFreeText(rs.getString(10 + colShift));
            tmp.setInternal(rs.getBoolean(11 + colShift));

            return tmp;
        }
        catch (SQLException e)
        {
            throw new JqmAdminApiInternalException(e);
        }
    }

    private static List<RUserDto> getUsers(DbConn cnx, String query_key, int colShift, Object... params)
    {
        List<RUserDto> res = new ArrayList<>();
        try
        {
            ResultSet rs = cnx.runSelect(query_key, params);
            RUserDto tmp = null;
            while (rs.next())
            {
                tmp = mapUser(rs, colShift, cnx);
                res.add(tmp);
            }
            rs.close();

            List<Integer> ids = new ArrayList<>();
            for (RUserDto dto : res)
            {
                ids.add(dto.getId());
            }

            rs = cnx.runSelect("role_select_id_for_user_list", ids);
            while (rs.next())
            {
                int userId = rs.getInt(2);
                int roleId = rs.getInt(1);

                for (RUserDto dto : res)
                {
                    if (dto.getId().equals(userId))
                    {
                        dto.addRole(roleId);
                    }
                }
            }
            rs.close();
        }
        catch (SQLException e)
        {
            throw new DatabaseException(e);
        }
        return res;
    }

    public static List<RUserDto> getUsers(DbConn cnx)
    {
        return getUsers(cnx, "user_select_all", 0);
    }

    public static RUserDto getUser(DbConn cnx, int id)
    {
        List<RUserDto> res = getUsers(cnx, "user_select_by_id", 0, id);
        if (res.size() == 1)
        {
            return res.get(0);
        }
        else
        {
            throw new JqmAdminApiUserException("no result");
        }
    }

    public static void changeUserPassword(DbConn cnx, int userId, String newPassword)
    {
        ByteSource salt = new SecureRandomNumberGenerator().nextBytes();
        String hash = new Sha512Hash(newPassword, salt, 100000).toHex();

        QueryResult qr = cnx.runUpdate("user_update_password_by_id", hash, salt.toHex(), userId);
        if (qr.nbUpdated == 0)
        {
            throw new JqmAdminApiUserException("user with this ID does not exist");
        }
    }

    public static void changeUserPassword(DbConn cnx, String userLogin, String newPassword)
    {
        List<RUserDto> dtos = getUsers(cnx, "user_select_by_key", 0, userLogin);
        if (dtos.size() == 0)
        {
            throw new JqmAdminApiUserException("Cannot update the password of a user which does not exist - given login was " + userLogin);
        }
        changeUserPassword(cnx, dtos.get(0).getId(), newPassword);
    }

    public static void upsertUser(DbConn cnx, RUserDto dto)
    {
        if (dto.getId() != null)
        {
            cnx.runUpdate("user_update_changed", dto.getLogin(), dto.getLocked(), dto.getExpirationDate(), dto.getEmail(),
                    dto.getFreeText(), dto.getId(), dto.getLogin(), dto.getLocked(), dto.getExpirationDate(), dto.getEmail(),
                    dto.getFreeText());

            // Password
            if (dto.getNewPassword() != null && !dto.getNewPassword().isEmpty())
            {
                changeUserPassword(cnx, dto.getId(), dto.getNewPassword());
            }

            // Roles
            cnx.runUpdate("user_remove_all_roles_by_id", dto.getId());
            for (int i : dto.getRoles())
            {
                cnx.runUpdate("user_add_role_by_id", dto.getId(), i);
            }
        }
        else
        {
            QueryResult r = cnx.runUpdate("user_insert", dto.getEmail(), dto.getExpirationDate(), dto.getFreeText(), null,
                    dto.getInternal(), false, dto.getLogin(), null);
            int newId = r.getGeneratedId();

            if (dto.getNewPassword() != null && !dto.getNewPassword().isEmpty())
            {
                changeUserPassword(cnx, newId, dto.getNewPassword());
            }

            for (int i : dto.getRoles())
            {
                cnx.runUpdate("user_add_role_by_id", newId, i);
            }
        }
    }

    public static void syncUsers(DbConn cnx, List<RUserDto> dtos)
    {
        for (RUserDto existing : getUsers(cnx))
        {
            boolean foundInNewSet = false;
            for (RUserDto newdto : dtos)
            {
                if (newdto.getId() != null && newdto.getId().equals(existing.getId()))
                {
                    foundInNewSet = true;
                    break;
                }
            }

            if (!foundInNewSet)
            {
                deleteUser(cnx, existing.getId());
            }
        }

        for (RUserDto dto : dtos)
        {
            upsertUser(cnx, dto);
        }
    }
}
