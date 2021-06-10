package com.enioka.jqm.jdbc;

import java.util.HashMap;
import java.util.Map;

/**
 * All the SQL templates used in the application. Templates are as pure ANSI 92 SQL as possible, if not are HSQLDB inspired and pass through a {@link DbAdapter} to run on specific databases.
 */
class DbImplBase
{
    /**
     *  Key convention is: entity_verb_descr<br>
     *  Placeholders are:
     *  <ul>
     *      <li>1: table name prefix (can contain schema information)</li>
     *  </ul>
     */
    static Map<String, String> queries = new HashMap<>();

    static {
        // VERSION
        queries.put("version_insert", "INSERT INTO __T__VERSION(ID, COMPONENT, VERSION_D1, COMPAT_D1, INSTALL_DATE) VALUES(JQM_PK.nextval, 'SCHEMA', ?, ?, CURRENT_TIMESTAMP)");
        queries.put("version_select_latest", "SELECT v1.VERSION_D1, v1.COMPAT_D1 FROM __T__VERSION v1 WHERE v1.ID = (SELECT MAX(v2.ID) AS OID FROM __T__VERSION v2 WHERE v2.COMPONENT='SCHEMA')");

        // NODE
        queries.put("node_insert", "INSERT INTO __T__NODE(ID, REPO_DELIVERABLE, DNS, ENABLED, JMX_REGISTRY_PORT, JMX_SERVER_PORT, "
                + "LOAD_API_ADMIN, LOAD_API_CLIENT, LOAD_API_SIMPLE, NAME, PORT, REPO_JOB_DEF, ROOT_LOG_LEVEL, STOP, REPO_TMP) "
                + "VALUES(JQM_PK.nextval, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        queries.put("node_delete_all", "DELETE FROM __T__NODE");
        queries.put("node_delete_by_id", "DELETE __T__NODE WHERE ID=?");
        queries.put("node_update_all_enable_ws", "UPDATE __T__NODE SET LOAD_API_SIMPLE=true, LOAD_API_CLIENT=true, LOAD_API_ADMIN=true, DNS='0.0.0.0'");
        queries.put("node_update_enable_ws_by_id", "UPDATE __T__NODE SET LOAD_API_SIMPLE=true, LOAD_API_CLIENT=true, LOAD_API_ADMIN=true, DNS='0.0.0.0' WHERE ID=?");
        queries.put("node_update_all_disable_ws", "UPDATE __T__NODE SET LOAD_API_CLIENT=false, LOAD_API_ADMIN=false");
        queries.put("node_update_all_disable_all_ws", "UPDATE __T__NODE SET LOAD_API_SIMPLE=false, LOAD_API_CLIENT=false, LOAD_API_ADMIN=false");
        queries.put("node_update_enabled_by_id", "UPDATE __T__NODE SET ENABLED=? WHERE ID=?");
        queries.put("node_update_port_by_id", "UPDATE __T__NODE SET PORT=? WHERE ID=?");
        queries.put("node_update_jmx_by_id", "UPDATE __T__NODE SET JMX_REGISTRY_PORT=?, JMX_SERVER_PORT=? WHERE ID=?");
        queries.put("node_update_alive_by_id", "UPDATE __T__NODE SET LAST_SEEN_ALIVE=CURRENT_TIMESTAMP WHERE ID=?");
        queries.put("node_update_has_stopped_by_id", "UPDATE __T__NODE SET LAST_SEEN_ALIVE=NULL, STOP=false WHERE ID=?");
        queries.put("node_update_stop_by_id", "UPDATE __T__NODE SET STOP=true WHERE ID=?");
        queries.put("node_update_all_log_level", "UPDATE __T__NODE SET ROOT_LOG_LEVEL=?");
        queries.put("node_update_changed_by_id", "UPDATE __T__NODE SET REPO_DELIVERABLE=?, DNS=?, ENABLED=?, JMX_REGISTRY_PORT=?, JMX_SERVER_PORT=?, "
                + "LOAD_API_ADMIN=?, LOAD_API_CLIENT=?, LOAD_API_SIMPLE=?, NAME=?, PORT=?, REPO_JOB_DEF=?, ROOT_LOG_LEVEL=?, STOP=?, REPO_TMP=? "
                + "WHERE ID=? AND NOT (REPO_DELIVERABLE=? AND DNS=? AND ENABLED=? AND JMX_REGISTRY_PORT=? AND JMX_SERVER_PORT=? AND "
                + "LOAD_API_ADMIN=? AND LOAD_API_CLIENT=? AND LOAD_API_SIMPLE=? AND NAME=? AND PORT=? AND REPO_JOB_DEF=? AND ROOT_LOG_LEVEL=? AND STOP=? AND REPO_TMP=?)");
        queries.put("node_select_all", "SELECT ID, REPO_DELIVERABLE, DNS, ENABLED, JMX_REGISTRY_PORT, JMX_SERVER_PORT, "
                + "LOAD_API_ADMIN, LOAD_API_CLIENT, LOAD_API_SIMPLE, NAME, PORT, REPO_JOB_DEF, ROOT_LOG_LEVEL, STOP, REPO_TMP, LAST_SEEN_ALIVE "
                + "FROM __T__NODE");
        queries.put("node_select_by_key", queries.get("node_select_all") + " WHERE NAME=?");
        queries.put("node_select_by_id", queries.get("node_select_all") + " WHERE ID=?");
        queries.put("node_select_connectdata_by_key", "SELECT DNS, PORT FROM __T__NODE WHERE NAME=?");

        // QUEUE
        queries.put("q_insert", "INSERT INTO __T__QUEUE(ID, DEFAULT_QUEUE, DESCRIPTION, NAME) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("q_delete_all", "DELETE FROM __T__QUEUE");
        queries.put("q_delete_by_id", "DELETE FROM __T__QUEUE WHERE ID=?");
        queries.put("q_update_default_none", "UPDATE __T__QUEUE SET DEFAULT_QUEUE=false");
        queries.put("q_update_default_by_id", "UPDATE __T__QUEUE SET DEFAULT_QUEUE=true WHERE ID=?");
        queries.put("q_update_all_fields_by_id", "UPDATE __T__QUEUE SET DEFAULT_QUEUE=?, DESCRIPTION=?, NAME=? WHERE ID=?");
        queries.put("q_update_changed_by_id", "UPDATE __T__QUEUE SET DEFAULT_QUEUE=?, DESCRIPTION=?, NAME=? WHERE ID=? AND NOT (DEFAULT_QUEUE=? AND DESCRIPTION=? AND NAME=?)");
        queries.put("q_select_count_all", "SELECT COUNT(1) FROM __T__QUEUE");
        queries.put("q_select_all", "SELECT ID, DEFAULT_QUEUE, DESCRIPTION, NAME FROM __T__QUEUE");
        queries.put("q_select_default", "SELECT ID, DEFAULT_QUEUE, DESCRIPTION, NAME FROM __T__QUEUE WHERE DEFAULT_QUEUE=true");
        queries.put("q_select_by_key", "SELECT ID, DEFAULT_QUEUE, DESCRIPTION, NAME FROM __T__QUEUE WHERE NAME=?");
        queries.put("q_select_by_id", "SELECT ID, DEFAULT_QUEUE, DESCRIPTION, NAME FROM __T__QUEUE WHERE ID=?");

        // DEPLOYMENT
        queries.put("dp_insert", "INSERT INTO __T__QUEUE_NODE_MAPPING(ID, ENABLED, LAST_MODIFIED, MAX_THREAD, POLLING_INTERVAL, NODE, QUEUE) VALUES(JQM_PK.nextval, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)");
        queries.put("dp_delete_all", "DELETE FROM __T__QUEUE_NODE_MAPPING");
        queries.put("dp_delete_for_node", "DELETE FROM __T__QUEUE_NODE_MAPPING WHERE NODE=?");
        queries.put("dp_delete_for_queue", "DELETE FROM __T__QUEUE_NODE_MAPPING WHERE QUEUE=?");
        queries.put("dp_delete_by_id", "DELETE FROM __T__QUEUE_NODE_MAPPING WHERE ID=?");
        queries.put("dp_update_interval_by_id", "UPDATE __T__QUEUE_NODE_MAPPING SET POLLING_INTERVAL=? WHERE ID=?");
        queries.put("dp_update_enable_by_queue_id", "UPDATE __T__QUEUE_NODE_MAPPING SET ENABLED=? WHERE QUEUE=?");
        queries.put("dp_update_threads_by_id", "UPDATE __T__QUEUE_NODE_MAPPING SET MAX_THREAD=? WHERE ID=?");
        queries.put("dp_update_changed_by_id", "UPDATE __T__QUEUE_NODE_MAPPING SET ENABLED=?, LAST_MODIFIED=CURRENT_TIMESTAMP, MAX_THREAD=?, POLLING_INTERVAL=?, NODE=?, QUEUE=? WHERE ID=? AND NOT "
                + "(ENABLED=? AND MAX_THREAD=? AND POLLING_INTERVAL=? AND NODE=? AND QUEUE=?)");
        queries.put("dp_select_by_id", "SELECT ID, ENABLED, LAST_MODIFIED, MAX_THREAD, POLLING_INTERVAL, NODE, QUEUE FROM __T__QUEUE_NODE_MAPPING WHERE ID=?");
        queries.put("dp_select_for_node", "SELECT ID, ENABLED, LAST_MODIFIED, MAX_THREAD, POLLING_INTERVAL, NODE, QUEUE FROM __T__QUEUE_NODE_MAPPING WHERE NODE=?");
        queries.put("dp_select_count_for_node", "SELECT COUNT(1) FROM __T__QUEUE_NODE_MAPPING WHERE NODE=?");
        queries.put("dp_select_enabled_for_queue", "SELECT ENABLED, MAX_THREAD FROM __T__QUEUE_NODE_MAPPING WHERE QUEUE=?");
        queries.put("dp_select_sum_queue_capacity", "SELECT SUM(dp.MAX_THREAD) FROM __T__QUEUE_NODE_MAPPING dp LEFT JOIN __T__NODE n ON n.ID = dp.NODE WHERE dp.ENABLED = true AND n.ENABLED = true AND dp.QUEUE = ?");
        queries.put("dp_select_all_with_names", "SELECT dp.ID, dp.ENABLED, dp.LAST_MODIFIED, dp.MAX_THREAD, dp.POLLING_INTERVAL, dp.NODE, dp.QUEUE, n.NAME, q.NAME FROM __T__QUEUE_NODE_MAPPING dp LEFT JOIN __T__NODE n ON n.ID=dp.NODE LEFT JOIN __T__QUEUE q ON q.ID=dp.QUEUE ");
        queries.put("dp_select_with_names_by_id", queries.get("dp_select_all_with_names") + " WHERE dp.ID=?");
        queries.put("dp_select_with_names_by_node_id", queries.get("dp_select_all_with_names") + " WHERE dp.NODE=?");

        // CL
        queries.put("cl_insert", "INSERT INTO __T__CL(ID, NAME, CHILD_FIRST, HIDDEN_CLASSES, TRACING, PERSISTENT, ALLOWED_RUNNERS) VALUES(JQM_PK.nextval, ?, ?, ?, ?, ?, ?)");
        queries.put("cl_delete_all", "DELETE FROM __T__CL");
        queries.put("cl_delete_by_id", "DELETE FROM __T__CL WHERE ID=?");
        queries.put("cl_update_all_fields_by_id", "UPDATE __T__CL SET NAME=?, CHILD_FIRST=?, HIDDEN_CLASSES=?, TRACING=?, PERSISTENT=?, ALLOWED_RUNNERS=? WHERE ID=?");
        queries.put("cl_select_all", "SELECT ID, NAME, CHILD_FIRST, HIDDEN_CLASSES, TRACING, PERSISTENT, ALLOWED_RUNNERS FROM __T__CL ");
        queries.put("cl_select_by_id", queries.get("cl_select_all") + " WHERE ID=?");
        queries.put("cl_select_by_key", queries.get("cl_select_all") + " WHERE NAME=?");

        // CL EVENT HANDLER
        queries.put("cleh_insert", "INSERT INTO __T__CL_HANDLER(ID, EVENT_TYPE, CLASS_NAME, CL) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("cleh_delete_all", "DELETE FROM __T__CL_HANDLER");
        queries.put("cleh_delete_all_for_cl", "DELETE FROM __T__CL_HANDLER WHERE CL=?");
        queries.put("cleh_select_all", "SELECT ID, EVENT_TYPE, CLASS_NAME, CL FROM __T__CL_HANDLER ");
        queries.put("cleh_select_all_for_cl", queries.get("cleh_select_all") + " WHERE CL=? ORDER BY ID");

        // CL EVENT HANDLER PARAMETER
        queries.put("clehprm_insert", "INSERT INTO __T__CL_HANDLER_PARAMETER(ID, KEYNAME, VALUE, CL_HANDLER) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("clehprm_delete_all", "DELETE FROM __T__CL_HANDLER_PARAMETER");
        queries.put("clehprm_delete_all_for_cleh", "DELETE FROM __T__CL_HANDLER_PARAMETER WHERE CL_HANDLER=?");
        queries.put("clehprm_delete_all_for_cl", "DELETE FROM __T__CL_HANDLER_PARAMETER WHERE CL_HANDLER IN(SELECT h.ID FROM CL_HANDLER h WHERE h.CL=?)"); // subquery - better multi db support.
        queries.put("clehprm_select_all", "SELECT ID, KEYNAME, VALUE, CL_HANDLER FROM __T__CL_HANDLER_PARAMETER ");
        queries.put("cleh_select_all_for_cleh", queries.get("clehprm_select_all") + " WHERE CL_HANDLER=?");

        // JOB DEF
        queries.put("jd_insert", "INSERT INTO __T__JOB_DEFINITION(ID, APPLICATION, JD_KEY, CL, "
                + "DESCRIPTION, ENABLED, EXTERNAL, HIGHLANDER, "
                + "PATH, CLASS_NAME, JAVA_OPTS, KEYWORD1, KEYWORD2, KEYWORD3, ALERT_AFTER_SECONDS, "
                + "MODULE, PATH_TYPE, QUEUE) "
                + "VALUES(JQM_PK.nextval, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        queries.put("jd_delete_all", "DELETE FROM __T__JOB_DEFINITION");
        queries.put("jd_delete_by_id", "DELETE FROM __T__JOB_DEFINITION WHERE ID=?");
        queries.put("jd_update_all_fields_by_id", "UPDATE __T__JOB_DEFINITION SET APPLICATION=?, JD_KEY=?, "
                + "DESCRIPTION=?, ENABLED=?, EXTERNAL=?, HIGHLANDER=?, "
                + "PATH=?, CLASS_NAME=?, JAVA_OPTS=?, KEYWORD1=?, KEYWORD2=?, KEYWORD3=?, ALERT_AFTER_SECONDS=?, "
                + "MODULE=?, PATH_TYPE=?, CL=?, QUEUE=? "
                + "WHERE ID=?");
        queries.put("jd_update_set_external_by_id", "UPDATE __T__JOB_DEFINITION SET EXTERNAL=true WHERE ID=?");
        queries.put("jd_update_set_enabled_by_id", "UPDATE __T__JOB_DEFINITION SET ENABLED=? WHERE ID=?");
        queries.put("jd_update_set_queue_by_key", "UPDATE __T__JOB_DEFINITION SET QUEUE=? WHERE JD_KEY=?");
        queries.put("jd_select_all", "SELECT ID, APPLICATION, JD_KEY, CL, "
                + "DESCRIPTION, ENABLED, EXTERNAL, HIGHLANDER, "
                + "PATH, CLASS_NAME, JAVA_OPTS, KEYWORD1, KEYWORD2, KEYWORD3, ALERT_AFTER_SECONDS, "
                + "MODULE, PATH_TYPE, QUEUE, PRIORITY FROM __T__JOB_DEFINITION");
        queries.put("jd_select_by_id", queries.get("jd_select_all") + " WHERE ID=?");
        queries.put("jd_select_by_key", queries.get("jd_select_all") + " WHERE JD_KEY=?");
        queries.put("jd_select_by_tag_app", queries.get("jd_select_all") + " WHERE APPLICATION=?");
        queries.put("jd_select_by_queue", queries.get("jd_select_all") + " WHERE QUEUE=?");

        // JOB DEF PRM
        queries.put("jdprm_insert", "INSERT INTO __T__JOB_DEFINITION_PARAMETER(ID, KEYNAME, VALUE, JOBDEF) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("jdprm_delete_all", "DELETE FROM __T__JOB_DEFINITION_PARAMETER");
        queries.put("jdprm_delete_all_for_jd", "DELETE FROM __T__JOB_DEFINITION_PARAMETER WHERE JOBDEF=?");
        queries.put("jdprm_select_all_for_jd", "SELECT ID, KEYNAME, VALUE, JOBDEF FROM __T__JOB_DEFINITION_PARAMETER WHERE JOBDEF=?");
        queries.put("jdprm_select_all_for_jd_list", "SELECT ID, KEYNAME, VALUE, JOBDEF FROM __T__JOB_DEFINITION_PARAMETER WHERE JOBDEF IN(UNNEST(?))");
        queries.put("jdprm_select_all", "SELECT ID, KEYNAME, VALUE, JOBDEF FROM __T__JOB_DEFINITION_PARAMETER ORDER BY JOBDEF");

        // SCHEDULED JOBS
        queries.put("sj_insert", "INSERT INTO __T__JOB_SCHEDULE(ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED) VALUES(JQM_PK.nextval, ?, ?, ?, ?, CURRENT_TIMESTAMP)");
        queries.put("sj_delete_all", "DELETE FROM __T__JOB_SCHEDULE");
        queries.put("sj_delete_all_for_jd", "DELETE FROM __T__JOB_SCHEDULE WHERE JOBDEF=?");
        queries.put("sj_delete_by_id", "DELETE FROM __T__JOB_SCHEDULE WHERE ID=?");
        queries.put("sj_update_all_fields_by_id", "UPDATE __T__JOB_SCHEDULE SET CRON_EXPRESSION=?, QUEUE=?, PRIORITY=?, LAST_UPDATED=CURRENT_TIMESTAMP WHERE ID=?");
        queries.put("sj_update_cron_by_id", "UPDATE __T__JOB_SCHEDULE SET CRON_EXPRESSION=?, LAST_UPDATED=CURRENT_TIMESTAMP WHERE ID=?");
        queries.put("sj_update_queue_by_id", "UPDATE __T__JOB_SCHEDULE SET QUEUE=?, LAST_UPDATED=CURRENT_TIMESTAMP WHERE ID=?");
        queries.put("sj_update_priority_by_id", "UPDATE __T__JOB_SCHEDULE SET PRIORITY=?, LAST_UPDATED=CURRENT_TIMESTAMP WHERE ID=?");
        queries.put("sj_select_all", "SELECT ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED FROM __T__JOB_SCHEDULE ORDER BY ID ");
        queries.put("sj_select_by_id", "SELECT ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED FROM __T__JOB_SCHEDULE WHERE ID=? ");
        queries.put("sj_select_updated",  "SELECT ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED FROM __T__JOB_SCHEDULE WHERE LAST_UPDATED > ?");
        queries.put("sj_select_for_jd",  "SELECT ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED FROM __T__JOB_SCHEDULE WHERE JOBDEF = ?");
        queries.put("sj_select_for_jd_list",  "SELECT ID, CRON_EXPRESSION, JOBDEF, QUEUE, PRIORITY, LAST_UPDATED FROM __T__JOB_SCHEDULE WHERE JOBDEF IN(UNNEST(?)) ORDER BY ID");

        // SCHEDULED JOBS PARAMETERS
        queries.put("sjprm_insert", "INSERT INTO __T__JOB_SCHEDULE_PARAMETER(ID, KEYNAME, VALUE, JOB_SCHEDULE) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("sjprm_delete_all", "DELETE FROM __T__JOB_SCHEDULE_PARAMETER");
        queries.put("sjprm_delete_all_for_sj", "DELETE FROM __T__JOB_SCHEDULE_PARAMETER WHERE JOB_SCHEDULE=?");
        queries.put("sjprm_delete_all_for_jd", "DELETE FROM __T__JOB_SCHEDULE_PARAMETER WHERE JOB_SCHEDULE IN (SELECT js.ID FROM __T__JOB_SCHEDULE js WHERE js.JOBDEF = ?)");
        queries.put("sjprm_select_all", "SELECT ID, KEYNAME, VALUE, JOB_SCHEDULE FROM __T__JOB_SCHEDULE_PARAMETER ");
        queries.put("sjprm_select_for_sj_list",  queries.get("sjprm_select_all") + " WHERE JOB_SCHEDULE IN(UNNEST(?)) ORDER BY JOB_SCHEDULE, ID");

        // JOB INSTANCE
        queries.put("ji_insert_enqueue", "INSERT INTO __T__JOB_INSTANCE (ID, DATE_ENQUEUE, EMAIL, APPLICATION, "
                + "KEYWORD1, KEYWORD2, KEYWORD3, MODULE, INTERNAL_POSITION, PARENT, PROGRESS, SESSION_KEY, "
                + "STATUS, USERNAME, JOBDEF, QUEUE, HIGHLANDER, FROM_SCHEDULE, DATE_NOT_BEFORE, PRIORITY, INSTRUCTION) "
                + "VALUES(JQM_PK.nextval, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, UNIX_MILLIS(), ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        queries.put("ji_delete_all", "DELETE FROM __T__JOB_INSTANCE");
        queries.put("ji_delete_by_id", "DELETE FROM __T__JOB_INSTANCE WHERE ID = ?");
        queries.put("ji_delete_waiting_in_queue_id", "DELETE FROM __T__JOB_INSTANCE WHERE QUEUE = ? AND STATUS IN ('HOLDED', 'SUBMITTED', 'SCHEDULED')");
        queries.put("jj_update_cancel_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS='CANCELLED' WHERE ID=? AND (STATUS='SUBMITTED' OR STATUS='SCHEDULED' OR STATUS='HOLDED')");
        queries.put("jj_update_kill_by_id", "UPDATE __T__JOB_INSTANCE SET INSTRUCTION='KILL' WHERE ID=?");
        queries.put("jj_update_pause_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS='HOLDED' WHERE ID=? AND STATUS='SUBMITTED'");
        queries.put("jj_update_resume_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS='SUBMITTED' WHERE ID=? AND STATUS='HOLDED'");
        queries.put("jj_update_instruction_pause_by_id", "UPDATE __T__JOB_INSTANCE SET INSTRUCTION='PAUSE' WHERE ID=? AND STATUS IN ('RUNNING', 'HOLDED', 'SUBMITTED', 'ATTRIBUTED', 'SCHEDULED')");
        queries.put("jj_update_instruction_resume_by_id", "UPDATE __T__JOB_INSTANCE SET INSTRUCTION='RUN' WHERE ID=? AND INSTRUCTION = 'PAUSE'");
        queries.put("jj_update_queue_by_id", "UPDATE __T__JOB_INSTANCE SET QUEUE=? WHERE ID=? AND STATUS IN('SUBMITTED', 'HOLDED', 'SCHEDULED')");
        queries.put("jj_update_priority_by_id", "UPDATE __T__JOB_INSTANCE SET PRIORITY=? WHERE ID=?");
        queries.put("jj_update_notbefore_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS='SCHEDULED', DATE_NOT_BEFORE=? WHERE ID=? AND STATUS IN('SCHEDULED', 'SUBMITTED')");
        queries.put("jj_update_rank_by_id", "UPDATE __T__JOB_INSTANCE SET INTERNAL_POSITION=? WHERE ID=? AND STATUS='SUBMITTED'");
        queries.put("jj_update_progress_by_id", "UPDATE __T__JOB_INSTANCE SET PROGRESS=? WHERE ID=?");
        queries.put("jj_update_run_by_id", "UPDATE __T__JOB_INSTANCE SET DATE_START=CURRENT_TIMESTAMP, STATUS='RUNNING' WHERE ID=? AND (STATUS='ATTRIBUTED' OR STATUS='RUNNING')");
        queries.put("debug_jj_update_node_by_id", "UPDATE __T__JOB_INSTANCE SET NODE=? WHERE ID=?");
        queries.put("debug_jj_update_status_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS=? WHERE ID=?");
        queries.put("ji_select_current_pos", "SELECT COUNT(ji) FROM __T__JOB_INSTANCE ji WHERE ji.INTERNAL_POSITION < ? AND ji.status = 'SUBMITTED' AND QUEUE=?");
        queries.put("ji_select_count_all", "SELECT COUNT(1) FROM __T__JOB_INSTANCE");
        queries.put("ji_select_count_running", "SELECT COUNT(1) FROM __T__JOB_INSTANCE WHERE STATUS='RUNNING'");
        queries.put("ji_select_count_by_jd", "SELECT COUNT(1) FROM __T__JOB_INSTANCE WHERE JOBDEF=?");
        queries.put("ji_select_count_by_node", "SELECT COUNT(1) FROM __T__JOB_INSTANCE WHERE NODE=?");
        queries.put("ji_select_count_by_queue", "SELECT COUNT(1) FROM __T__JOB_INSTANCE WHERE QUEUE=?");
        queries.put("ji_select_all", "SELECT ji.ID, ji.DATE_ATTRIBUTION, ji.DATE_ENQUEUE, ji.EMAIL, ji.DATE_START, ji.APPLICATION, ji.KEYWORD1, ji.KEYWORD2, "
                + "ji.KEYWORD3, ji.MODULE, ji.INTERNAL_POSITION, ji.PARENT, ji.PROGRESS, ji.SESSION_KEY, ji.STATUS, ji.USERNAME, ji.JOBDEF, ji.NODE, ji.QUEUE, ji.HIGHLANDER, ji.FROM_SCHEDULE, ji.PRIORITY, ji.INSTRUCTION, ji.DATE_NOT_BEFORE, "
                + "q.ID AS Q_ID, q.DEFAULT_QUEUE, q.DESCRIPTION AS Q_DESCRIPTION, q.NAME AS Q_NAME, "
                + "jd.ID AS JD_ID, jd.APPLICATION AS JD_APPLICATION, jd.JD_KEY, jd.CL, "
                + "jd.DESCRIPTION AS JD_DESCRITPION, jd.ENABLED AS JD_ENABLED, jd.EXTERNAL, jd.HIGHLANDER AS JD_HIGHLANDER, "
                + "jd.PATH, jd.CLASS_NAME, jd.JAVA_OPTS, jd.KEYWORD1 AS JD_KW1, jd.KEYWORD2 AS JD_KW2, jd.KEYWORD3 AS JD_KW3, jd.ALERT_AFTER_SECONDS, "
                + "jd.MODULE AS JD_MODULE, jd.PATH_TYPE, jd.QUEUE AS JD_QUEUE, "
                + "n.ID AS N_ID, n.REPO_DELIVERABLE, n.DNS, n.ENABLED AS N_ENABLED, n.JMX_REGISTRY_PORT, n.JMX_SERVER_PORT, "
                + "n.LOAD_API_ADMIN, n.LOAD_API_CLIENT, n.LOAD_API_SIMPLE, n.NAME AS N_NAME, n.PORT, n.REPO_JOB_DEF, n.ROOT_LOG_LEVEL, n.STOP, n.REPO_TMP, n.LAST_SEEN_ALIVE "
                + "FROM __T__JOB_INSTANCE ji LEFT JOIN __T__QUEUE q ON ji.QUEUE=q.ID LEFT JOIN __T__JOB_DEFINITION jd ON ji.JOBDEF=jd.ID LEFT JOIN __T__NODE n ON ji.NODE=n.ID ");
        queries.put("ji_select_by_id", queries.get("ji_select_all") + " WHERE ji.ID=?");
        queries.put("ji_select_by_queue", queries.get("ji_select_all") + " WHERE ji.QUEUE=? ORDER BY INTERNAL_POSITION");
        queries.put("ji_select_by_node", queries.get("ji_select_all") + " WHERE ji.NODE=?");
        queries.put("ji_select_existing_highlander", "SELECT ID FROM __T__JOB_INSTANCE WHERE JOBDEF=? AND STATUS='SUBMITTED'");
        queries.put("ji_select_existing_highlander_2", "SELECT COUNT(1) FROM __T__JOB_INSTANCE WHERE JOBDEF=? AND STATUS IN('ATTRIBUTED', 'RUNNING')");
        queries.put("ji_select_changequeuepos_by_id", "SELECT QUEUE, INTERNAL_POSITION FROM __T__JOB_INSTANCE WHERE ID=? AND STATUS='SUBMITTED'");
        queries.put("ji_select_instruction_by_id", "SELECT INSTRUCTION FROM __T__JOB_INSTANCE WHERE ID=?");
        queries.put("ji_select_priority_by_id", "SELECT PRIORITY FROM __T__JOB_INSTANCE WHERE ID=?");
        queries.put("ji_select_execution_date_by_id", "SELECT DATE_START FROM __T__JOB_INSTANCE WHERE ID=?");
        queries.put("ji_select_cnx_data_by_id", "SELECT DNS||':'||PORT AS HOST FROM __T__JOB_INSTANCE ji LEFT JOIN __T__NODE n ON ji.NODE = n.ID WHERE ji.ID=?");
        queries.put("ji_select_instructions_by_node", "SELECT ji.ID, ji.INSTRUCTION FROM __T__JOB_INSTANCE ji WHERE ji.STATUS='RUNNING' AND ji.INSTRUCTION <> 'RUN' AND ji.NODE=?");

        queries.put("ji_update_delayed", "UPDATE __T__JOB_INSTANCE SET STATUS='SUBMITTED' WHERE STATUS='SCHEDULED' AND DATE_NOT_BEFORE <= CURRENT_TIMESTAMP");
        queries.put("ji_select_poll",queries.get("ji_select_all") + " WHERE ji.QUEUE = ? AND ji.STATUS='SUBMITTED' ORDER BY ji.PRIORITY DESC, ji.INTERNAL_POSITION");
        queries.put("ji_update_status_by_id", "UPDATE __T__JOB_INSTANCE SET STATUS='ATTRIBUTED', NODE=? WHERE STATUS='SUBMITTED' AND ID=?");

        // HISTORY
        queries.put("history_insert_with_end_date", "INSERT INTO __T__HISTORY(ID, JD_APPLICATION, JD_KEY, DATE_ATTRIBUTION, EMAIL, "
                + "DATE_END, DATE_ENQUEUE, DATE_START, HIGHLANDER, INSTANCE_APPLICATION, INSTANCE_KEYWORD1, "
                + "INSTANCE_KEYWORD2, INSTANCE_KEYWORD3, INSTANCE_MODULE, JD_KEYWORD1, JD_KEYWORD2, JD_KEYWORD3, JD_MODULE, "
                + "NODE_NAME, PARENT, PROGRESS, QUEUE_NAME, RETURN_CODE, SESSION_KEY, STATUS, USERNAME, JOBDEF, "
                + "NODE, QUEUE, FROM_SCHEDULE, PRIORITY, DATE_NOT_BEFORE) "
                + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        queries.put("history_insert", queries.get("history_insert_with_end_date").replace("(?, ?, ?, ?, ?, ?",  "(?, ?, ?, ?, ?, CURRENT_TIMESTAMP"));

        queries.put("history_delete_all", "DELETE FROM __T__HISTORY");
        queries.put("history_delete_by_id", "DELETE FROM __T__HISTORY WHERE ID=?");
        queries.put("history_select_count_all", "SELECT COUNT(1) FROM __T__HISTORY");
        queries.put("history_select_count_for_poller", "SELECT COUNT(1) FROM __T__HISTORY WHERE QUEUE=? AND NODE=?");
        queries.put("history_select_count_last_mn_for_poller", "SELECT COUNT(1)/60 FROM __T__HISTORY WHERE QUEUE=? AND NODE=? AND DATE_END > (CURRENT_TIMESTAMP - 1 MINUTE)");
        queries.put("history_select_count_ended", "SELECT COUNT(1) FROM __T__HISTORY WHERE STATUS='ENDED'");
        queries.put("history_select_count_notended", "SELECT COUNT(1) FROM __T__HISTORY WHERE STATUS<>'ENDED'");
        queries.put("history_select_reenqueue_by_id", "SELECT JD_APPLICATION, JD_KEY, EMAIL, INSTANCE_KEYWORD1, INSTANCE_KEYWORD2, INSTANCE_KEYWORD3, INSTANCE_MODULE, PARENT, SESSION_KEY, USERNAME, STATUS FROM __T__HISTORY WHERE ID=?");
        queries.put("history_select_cnx_data_by_id", "SELECT DNS||':'||PORT AS HOST FROM __T__HISTORY h LEFT JOIN __T__NODE n ON h.NODE = n.ID WHERE h.ID=?");
        queries.put("history_select_state_by_id", "SELECT STATUS FROM __T__HISTORY WHERE ID=?");

        // DELIVERABLE
        queries.put("deliverable_insert",  "INSERT INTO __T__DELIVERABLE(ID, FILE_FAMILY, PATH, JOB_INSTANCE, ORIGINAL_FILE_NAME, RANDOM_ID) VALUES(JQM_PK.nextval, ?, ?, ?, ?, ?)");
        queries.put("deliverable_delete_all", "DELETE FROM __T__DELIVERABLE");
        queries.put("deliverable_select_all",  "SELECT ID, FILE_FAMILY, PATH, JOB_INSTANCE, ORIGINAL_FILE_NAME, RANDOM_ID FROM __T__DELIVERABLE");
        queries.put("deliverable_select_by_id", queries.get("deliverable_select_all") +  " WHERE ID=?");
        queries.put("deliverable_select_by_randomid", queries.get("deliverable_select_all") +  " WHERE RANDOM_ID=?");
        queries.put("deliverable_select_all_for_ji", queries.get("deliverable_select_all") +  " WHERE JOB_INSTANCE=?");

        // RUNTIME PRM
        queries.put("jiprm_insert", "INSERT INTO __T__JOB_INSTANCE_PARAMETER(ID, JOB_INSTANCE, KEYNAME, VALUE) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("jiprm_delete_all", "DELETE FROM __T__JOB_INSTANCE_PARAMETER ");
        queries.put("jiprm_delete_by_ji",queries.get("jiprm_delete_all") + " WHERE JOB_INSTANCE=?");
        queries.put("jiprm_select_by_ji", "SELECT ID, JOB_INSTANCE, KEYNAME, VALUE FROM __T__JOB_INSTANCE_PARAMETER WHERE JOB_INSTANCE=?");
        queries.put("jiprm_select_by_ji_list", "SELECT ID, JOB_INSTANCE, KEYNAME, VALUE FROM __T__JOB_INSTANCE_PARAMETER WHERE JOB_INSTANCE IN(UNNEST(?))");

        // MESSAGE
        queries.put("message_insert",  "INSERT INTO __T__MESSAGE(ID, JOB_INSTANCE, TEXT_MESSAGE) VALUES(JQM_PK.nextval, ?, ?)");
        queries.put("message_delete_all", "DELETE FROM __T__MESSAGE");
        queries.put("message_delete_by_ji",queries.get("message_delete_all") + " WHERE JOB_INSTANCE=?");
        queries.put("message_select_all", "SELECT ID, JOB_INSTANCE, TEXT_MESSAGE FROM __T__MESSAGE");
        queries.put("message_select_by_ji_list", queries.get("message_select_all") + " WHERE JOB_INSTANCE IN(UNNEST(?))");
        queries.put("message_select_count_all", "SELECT COUNT(1) FROM __T__MESSAGE");

        // JNDI
        queries.put("jndi_insert", "INSERT INTO __T__JNDI_OBJECT_RESOURCE(ID, AUTH, DESCRIPTION, FACTORY, LAST_MODIFIED, NAME, SINGLETON, TEMPLATE, TYPE) VALUES(JQM_PK.nextval, 'CONTAINER', ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)");
        queries.put("jndi_delete_all", "DELETE FROM __T__JNDI_OBJECT_RESOURCE");
        queries.put("jndi_delete_by_id", "DELETE FROM __T__JNDI_OBJECT_RESOURCE WHERE ID=?");
        queries.put("jndi_update_changed_by_id", "UPDATE __T__JNDI_OBJECT_RESOURCE SET AUTH=?, DESCRIPTION=?, FACTORY=?, LAST_MODIFIED=CURRENT_TIMESTAMP, NAME=?, SINGLETON=?, TEMPLATE=?, TYPE=? WHERE ID=? AND NOT (AUTH=? AND DESCRIPTION=? AND FACTORY=? AND NAME=? AND SINGLETON=? AND TEMPLATE=? AND TYPE=?)");
        queries.put("jndi_select_count_for_key", "SELECT COUNT(1) FROM __T__JNDI_OBJECT_RESOURCE WHERE NAME=?");
        queries.put("jndi_select_count_changed", "SELECT COUNT(1) FROM __T__JNDI_OBJECT_RESOURCE r RIGHT JOIN __T__JNDI_OR_PARAMETER p ON p.JNDI_OR = r.ID WHERE r.LAST_MODIFIED > ? OR p.LAST_MODIFIED > ?");
        queries.put("jndi_select_all", "SELECT ID, NAME, AUTH, TYPE, FACTORY, DESCRIPTION, TEMPLATE, SINGLETON, LAST_MODIFIED FROM __T__JNDI_OBJECT_RESOURCE");
        queries.put("jndi_select_by_key", queries.get("jndi_select_all") + " WHERE NAME=?");
        queries.put("jndi_select_by_id", queries.get("jndi_select_all") + " WHERE ID=?");

        // JNDI PRM
        queries.put("jndiprm_insert", "INSERT INTO __T__JNDI_OR_PARAMETER(ID, KEYNAME, LAST_MODIFIED, VALUE, JNDI_OR) VALUES(JQM_PK.nextval, ?, CURRENT_TIMESTAMP, ?, ?)");
        queries.put("jndiprm_delete_all", "DELETE FROM __T__JNDI_OR_PARAMETER");
        queries.put("jndiprm_delete_for_resource", "DELETE FROM __T__JNDI_OR_PARAMETER WHERE JNDI_OR=?");
        queries.put("jndiprm_delete_by_id", "DELETE FROM __T__JNDI_OR_PARAMETER WHERE ID=?");
        queries.put("jndiprm_update_value_by_key", "UPDATE __T__JNDI_OR_PARAMETER SET VALUE=?, LAST_MODIFIED=CURRENT_TIMESTAMP WHERE KEYNAME=?");
        queries.put("jndiprm_update_changed_by_id", "UPDATE __T__JNDI_OR_PARAMETER SET VALUE=?, LAST_MODIFIED=CURRENT_TIMESTAMP WHERE JNDI_OR=? AND KEYNAME=? AND NOT (VALUE=?)");
        queries.put("jndiprm_select_all_in_jndisrc", "SELECT ID, KEYNAME, LAST_MODIFIED, VALUE FROM __T__JNDI_OR_PARAMETER WHERE JNDI_OR=?");
        queries.put("jndiprm_select_all_in_jndisrc_list", "SELECT ID, KEYNAME, LAST_MODIFIED, VALUE, JNDI_OR FROM __T__JNDI_OR_PARAMETER WHERE JNDI_OR IN(UNNEST(?))");

        // PKI
        queries.put("pki_insert", "INSERT INTO __T__PKI(ID, PEM_CERT, PEM_PK, PRETTY_NAME) VALUES(JQM_PK.nextval, ?, ?, ?)");
        queries.put("pki_delete_all", "DELETE FROM __T__PKI");
        queries.put("pki_select_by_key", "SELECT ID, PEM_CERT, PEM_PK, PRETTY_NAME FROM __T__PKI WHERE PRETTY_NAME=?");

        // R-ROLE
        queries.put("role_insert", "INSERT INTO __T__RROLE(ID, DESCRIPTION, NAME) VALUES(JQM_PK.nextval, ?, ?)");
        queries.put("role_delete_all", "DELETE FROM __T__RROLE");
        queries.put("role_delete_by_id", "DELETE FROM __T__RROLE WHERE ID=?");
        queries.put("role_update_all_by_id", "UPDATE __T__RROLE SET NAME=?, DESCRIPTION=? WHERE ID=? ");
        queries.put("role_select_all", "SELECT r.ID, r.NAME, r.DESCRIPTION FROM __T__RROLE r ");
        queries.put("role_select_all_for_user", "SELECT r.ID, r.NAME, r.DESCRIPTION FROM __T__RROLE r RIGHT JOIN __T__RROLE_RUSER a ON a.ROLE = r.ID WHERE a.ACCOUNT=?");
        queries.put("role_select_id_for_user_list", "SELECT a.ROLE, a.ACCOUNT FROM __T__RROLE_RUSER a WHERE a.ACCOUNT IN(UNNEST(?))");
        queries.put("role_select_by_key", "SELECT ID, NAME, DESCRIPTION FROM __T__RROLE r WHERE NAME=?");

        // R-PERMISSION
        queries.put("perm_insert", "INSERT INTO __T__RPERMISSION(ID, NAME, ROLE) VALUES(JQM_PK.nextval, ?, ?)");
        queries.put("perm_delete_all", "DELETE FROM __T__RPERMISSION");
        queries.put("perm_delete_for_role", "DELETE FROM __T__RPERMISSION WHERE ROLE=?");
        queries.put("perm_select_all_in_role", "SELECT ID, NAME, ROLE FROM __T__RPERMISSION WHERE ROLE=?");
        queries.put("perm_select_all_in_role_list", "SELECT ID, NAME, ROLE FROM __T__RPERMISSION WHERE ROLE IN(UNNEST(?))");

        // R-USER
        queries.put("user_insert", "INSERT INTO __T__RUSER(ID, CREATION_DATE, EMAIL, EXPIRATION_DATE, FREETEXT, HASHSALT, INTERNAL, LAST_MODIFIED, LOCKED, LOGIN, PASSWORD) VALUES(JQM_PK.nextval, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)");
        queries.put("user_delete_all", "DELETE FROM __T__RUSER");
        queries.put("user_delete_by_id", "DELETE FROM __T__RUSER WHERE ID=?");
        queries.put("user_delete_expired_internal", "DELETE FROM __T__RUSER WHERE internal=true AND EXPIRATION_DATE < CURRENT_TIMESTAMP");
        queries.put("user_add_role_by_name", "INSERT INTO __T__RROLE_RUSER(ID, ACCOUNT, ROLE) VALUES(JQM_PK.nextval, ?, (SELECT r.ID FROM __T__RROLE r WHERE r.NAME=?))");
        queries.put("user_add_role_by_id", "INSERT INTO __T__RROLE_RUSER(ID, ACCOUNT, ROLE) VALUES(JQM_PK.nextval, ?, ?)");
        queries.put("user_remove_all_roles_by_id", "DELETE FROM __T__RROLE_RUSER WHERE ACCOUNT=?");
        queries.put("user_remove_role", "DELETE FROM __T__RROLE_RUSER WHERE ROLE=? AND ACCOUNT=?");
        queries.put("user_update_enable_by_id", "UPDATE __T__RUSER SET LOCKED=false WHERE ID=?");
        queries.put("user_update_password_by_id", "UPDATE __T__RUSER SET PASSWORD=?, HASHSALT=? WHERE ID=?");
        queries.put("user_update_changed", "UPDATE __T__RUSER SET LOGIN=?, LOCKED=?, EXPIRATION_DATE=?, LAST_MODIFIED=CURRENT_TIMESTAMP, EMAIL=?, FREETEXT=? WHERE ID=? AND NOT (LOGIN=? AND LOCKED=? AND EXPIRATION_DATE=? AND EMAIL=? AND FREETEXT=? )");
        queries.put("user_select_all_in_role", "SELECT ID, LOGIN, PASSWORD, HASHSALT, LOCKED, EXPIRATION_DATE, CREATION_DATE, LAST_MODIFIED, EMAIL, FREETEXT, INTERNAL FROM __T__RUSER u RIGHT JOIN __T__RROLE_RUSER a ON a.USER = u.ID WHERE a.ROLE=?");
        queries.put("user_select_all",         "SELECT ID, LOGIN, PASSWORD, HASHSALT, LOCKED, EXPIRATION_DATE, CREATION_DATE, LAST_MODIFIED, EMAIL, FREETEXT, INTERNAL FROM __T__RUSER ");
        queries.put("user_select_by_key",      queries.get("user_select_all") +  " WHERE LOGIN=?");
        queries.put("user_select_by_id",       queries.get("user_select_all") +  " WHERE ID=?");
        queries.put("user_select_count_by_key", "SELECT COUNT(1) FROM __T__RUSER WHERE LOGIN=?");
        queries.put("user_select_id_by_key", "SELECT ID FROM __T__RUSER WHERE LOGIN=?");
        queries.put("user_select_count_using_role", "SELECT COUNT(1) FROM __T__RROLE_RUSER WHERE ROLE=?");

        // GLOBAL PRM
        queries.put("globalprm_insert", "INSERT INTO __T__GLOBAL_PARAMETER(ID, KEYNAME, VALUE, LAST_MODIFIED) VALUES(JQM_PK.nextval, ?, ?, CURRENT_TIMESTAMP)");
        queries.put("globalprm_update_value_by_key", "UPDATE __T__GLOBAL_PARAMETER SET VALUE=?, LAST_MODIFIED=CURRENT_TIMESTAMP WHERE KEYNAME=?");
        queries.put("globalprm_delete_all", "DELETE FROM __T__GLOBAL_PARAMETER");
        queries.put("globalprm_delete_by_id", "DELETE FROM __T__GLOBAL_PARAMETER WHERE ID=?");
        queries.put("globalprm_select_all", "SELECT ID, KEYNAME, VALUE, LAST_MODIFIED FROM __T__GLOBAL_PARAMETER");
        queries.put("globalprm_select_by_key", queries.get("globalprm_select_all") + " WHERE KEYNAME=?");
        queries.put("globalprm_select_by_id", queries.get("globalprm_select_all") + " WHERE ID=?");
        queries.put("globalprm_select_count_modified_jetty", "SELECT COUNT(1) FROM __T__GLOBAL_PARAMETER WHERE LAST_MODIFIED > ? AND KEYNAME IN('disableWsApi', 'enableWsApiSsl', 'enableInternalPki', 'pfxPassword', 'enableWsApiAuth')");

        // WITNESS
        queries.put("w_insert", "INSERT INTO __T__WITNESS(ID, KEYNAME, NODE, LATEST_CONTACT) VALUES(JQM_PK.nextval, 'SCHEDULER', ?, CURRENT_TIMESTAMP)");
        queries.put("w_update_take", "UPDATE __T__WITNESS SET NODE=?, LATEST_CONTACT=CURRENT_TIMESTAMP WHERE KEYNAME='SCHEDULER' AND (LATEST_CONTACT IS NULL OR NODE IS NULL OR NODE=? OR (NODE<>? AND LATEST_CONTACT < (CURRENT_TIMESTAMP - ? SECOND)))");
    }

}

