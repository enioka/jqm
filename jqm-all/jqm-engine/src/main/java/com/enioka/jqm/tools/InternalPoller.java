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
package com.enioka.jqm.tools;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.naming.spi.NamingManager;

import com.enioka.jqm.jdbc.DatabaseUnreachableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.jdbc.DatabaseException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.Instruction;
import com.enioka.jqm.model.Node;

/**
 * The internal poller is responsible for doing all the repetitive tasks of an engine (excluding polling queues). Namely: check if
 * {@link Node#isStop()} has become true (stop order) and update {@link Node#setLastSeenAlive(java.util.Calendar)} to make visible to the
 * whole cluster that the engine is still alive and that no other engine should start with the same node name.
 */
class InternalPoller implements Runnable
{
    private static Logger jqmlogger = LoggerFactory.getLogger(InternalPoller.class);
    private boolean run = true;
    private JqmEngine engine = null;
    private long step;
    private Node node = null;
    private Semaphore loop = new Semaphore(0);

    InternalPoller(JqmEngine e)
    {
        this.engine = e;
        DbConn cnx = Helpers.getNewDbSession();

        // Get configuration data
        this.node = this.engine.getNode();
        this.step = Long.parseLong(GlobalParameter.getParameter(cnx, "internalPollingPeriodMs", "60000"));
        cnx.close();
    }

    void stop()
    {
        jqmlogger.info("Internal poller has received a stop request");
        this.run = false;
        forceLoop();
    }

    void forceLoop()
    {
        this.loop.release(1);
    }

    @Override
    public void run()
    {
        Thread.currentThread().setName("INTERNAL_POLLER;polling orders;");
        jqmlogger.info("Start of the internal poller");
        Calendar lastJndiPurge = Calendar.getInstance();

        // Launch main loop
        while (true)
        {
            try
            {
                loop.tryAcquire(this.step, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                run = false;
            }
            if (!run)
            {
                break;
            }

            // Get session
            try (DbConn cnx = Helpers.getNewDbSession())
            {
                // Check if stop order
                try
                {
                    node = Node.select_single(cnx, "node_select_by_id", node.getId());
                }
                catch (NoResultException e)
                {
                    node = null;
                }
                if (node == null || node.isStop())
                {
                    jqmlogger.info("Node has received a stop order from the database or was removed from the database");
                    jqmlogger.trace(
                            "At stop order time, there are " + this.engine.getCurrentlyRunningJobCount() + " jobs running in the node");
                    this.run = false;
                    this.engine.stop();
                    break;
                }

                // Engine handler is allowed to do changes on configuration changes.
                if (this.engine.getHandler() != null)
                {
                    this.engine.getHandler().onConfigurationChanged(node);
                }

                // I am alive
                cnx.runUpdate("node_update_alive_by_id", node.getId());
                cnx.commit();

                // Have queue bindings changed, or is engine disabled?
                this.engine.syncPollers(cnx, node);

                // Should JNDI cache be purged?
                Calendar bflkpm = Calendar.getInstance();
                int i = cnx.runSelectSingle("jndi_select_count_changed", Integer.class, lastJndiPurge, lastJndiPurge);
                if (i > 0L)
                {
                    try
                    {
                        ((JndiContext) NamingManager.getInitialContext(null)).resetSingletons();
                        lastJndiPurge = bflkpm;
                    }
                    catch (Exception e)
                    {
                        jqmlogger.warn(
                                "Could not reset JNDI singleton resources. New parameters won't be used. Restart engine to update them.",
                                e);
                    }
                }

                // Should job instances be killed or changed priorities?
                try
                {
                    ResultSet rs = cnx.runSelect("ji_select_instructions_by_node", node.getId());
                    while (rs.next())
                    {
                        Integer jiid = rs.getInt(1);
                        String instr = rs.getString(2);
                        Instruction instruction;
                        try
                        {
                            instruction = Instruction.valueOf(instr);
                        }
                        catch (IllegalArgumentException ex2)
                        {
                            jqmlogger.warn("An unknown instruction was found and is ignored: " + instr);
                            continue;
                        }

                        this.engine.getRunningJobInstanceManager().handleInstruction(jiid, instruction);
                    }
                }
                catch (SQLException e)
                {
                    throw new DatabaseException(e);
                }

                // All engine pollings done!
            }
            catch (DatabaseUnreachableException e)
            {
                jqmlogger.error("connection to database lost - stopping internal poller");
                jqmlogger.trace("connection error was:", e.getCause());
                run = false;
                this.engine.startDbRestarter();
                break;
            }
            catch (RuntimeException e)
            {
                throw e;
            }
        }

        jqmlogger.info("End of the internal poller");
    }
}
