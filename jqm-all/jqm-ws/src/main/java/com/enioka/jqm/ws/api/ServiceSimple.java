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
package com.enioka.jqm.ws.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import com.enioka.admin.JqmAdminApiException;
import com.enioka.jqm.client.api.JobRequest;
import com.enioka.jqm.client.jdbc.api.JqmClientFactory;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.jdbc.NoResultException;
import com.enioka.jqm.model.Deliverable;
import com.enioka.jqm.model.Node;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal API designed to interact well with CLI tools such as schedulers. Some of its methods (file retrieval) are also used by the two
 * other sets of APIs.
 *
 */
@Component(service = ServiceSimple.class, configurationPolicy = ConfigurationPolicy.REQUIRE, scope = ServiceScope.SINGLETON)
@JaxrsResource
@Path("/simple")
public class ServiceSimple
{
    private static Logger log = LoggerFactory.getLogger(ServiceSimple.class);

    private Integer jqmNodeId = null;

    @Activate
    public void onServiceActivation(Map<String, Object> properties)
    {
        if (!properties.containsKey("jqmnodeid"))
        {
            throw new JqmAdminApiException("OSGi configuration for ServiceSimple does not contain a valid node ID");
        }

        jqmNodeId = (int) properties.get("jqmnodeid"); // small int - cannot be null.
        log.info("\tStarting ServiceSimple with node ID {}", jqmNodeId);
    }

    /////////////////////////////////////////////////////////////
    // Somes file APIs are only available if the node runs on top of JQM
    /////////////////////////////////////////////////////////////

    private Node getLocalNodeIfRunningOnJqm()
    {
        if (jqmNodeId != null)
        {
            // Running on a JQM node, not a standard servlet container.
            try (DbConn cnx = Helpers.getDbSession())
            {
                try
                {
                    return Node.select_single(cnx, "node_select_by_id", jqmNodeId);
                }
                catch (NoResultException e)
                {
                    throw new RuntimeException("invalid configuration: no node of ID " + jqmNodeId);
                }
            }
        }
        return null;
    }

    /////////////////////////////////////////////////////////////
    // The one and only really simple API
    /////////////////////////////////////////////////////////////

    @GET
    @Path("status")
    @Produces(MediaType.TEXT_PLAIN)
    public String getStatus(@QueryParam("id") int id)
    {
        return JqmClientFactory.getClient().getJob(id).getState().toString();
    }

    /////////////////////////////////////////////////////////////
    // File retrieval
    /////////////////////////////////////////////////////////////

    @GET
    @Path("stdout")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream getLogOut(@QueryParam("id") int id, @Context HttpServletResponse res, @Context SecurityContext security)
    {
        res.setHeader("Content-Disposition", "attachment; filename=" + id + ".stdout.txt");
        return getFile(FilenameUtils.concat("./logs", StringUtils.leftPad("" + id, 10, "0") + ".stdout.log"), security);
    }

    @GET
    @Path("stderr")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream getLogErr(@QueryParam("id") int id, @Context HttpServletResponse res, @Context SecurityContext security)
    {
        res.setHeader("Content-Disposition", "attachment; filename=" + id + ".stderr.txt");
        return getFile(FilenameUtils.concat("./logs", StringUtils.leftPad("" + id, 10, "0") + ".stderr.log"), security);
    }

    @GET
    @Path("file")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream getDeliverableStream(@QueryParam("id") String randomId, @Context HttpServletResponse res,
            @Context SecurityContext security)
    {
        Node n = getLocalNodeIfRunningOnJqm();
        if (n == null)
        {
            throw new ErrorDto("can only retrieve a file when the web app runs on top of JQM", "", 7, Status.BAD_REQUEST);
        }

        Deliverable d = null;
        try (DbConn cnx = Helpers.getDbSession())
        {
            List<Deliverable> dd = Deliverable.select(cnx, "deliverable_select_by_randomid", randomId);
            if (dd.isEmpty())
            {
                throw new NoResultException("requested file does not exist");
            }

            d = dd.get(0);
        }
        catch (NoResultException e)
        {
            throw new ErrorDto("Deliverable does not exist", 8, e, Status.BAD_REQUEST);
        }
        catch (Exception e)
        {
            throw new ErrorDto("Could not retrieve Deliverable metadata from database", 9, e, Status.INTERNAL_SERVER_ERROR);
        }

        String ext = FilenameUtils.getExtension(d.getOriginalFileName());
        res.setHeader("Content-Disposition", "attachment; filename=" + d.getFileFamily() + "." + d.getId() + "." + ext);
        return getFile(FilenameUtils.concat(n.getDlRepo(), d.getFilePath()), security);
    }

    private InputStream getFile(String path, @Context SecurityContext security)
    {
        try
        {
            log.debug("file retrieval service called by user " + getUserName(security) + " for file " + path);
            return new FileInputStream(path);
        }
        catch (FileNotFoundException e)
        {
            throw new ErrorDto("Could not find the desired file", 8, e, Status.NO_CONTENT);
        }
    }

    @GET
    @Path("enginelog")
    @Produces(MediaType.TEXT_PLAIN)
    public String getEngineLog(@QueryParam("latest") int latest)
    {
        Node n = getLocalNodeIfRunningOnJqm();
        if (n == null)
        {
            throw new ErrorDto("can only retrieve a file when the web app runs on top of JQM", "", 7, Status.BAD_REQUEST);
        }

        // Failsafe
        if (latest > 10000)
        {
            latest = 10000;
        }

        File f = new File(FilenameUtils.concat("./logs/", "jqm-" + n.getId() + ".log"));
        try (ReversedLinesFileReader r = new ReversedLinesFileReader(f, Charset.defaultCharset()))
        {
            StringBuilder sb = new StringBuilder(latest);
            String buf = r.readLine();
            int i = 1;
            while (buf != null && i <= latest)
            {
                sb.append(buf);
                sb.append(System.getProperty("line.separator"));
                i++;
                buf = r.readLine();
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            throw new ErrorDto("Could not return the desired file", 8, e, Status.NO_CONTENT);
        }
    }

    private String getUserName(@Context SecurityContext security)
    {
        if (security != null && security.getUserPrincipal() != null && security.getUserPrincipal().getName() != null)
        {
            return security.getUserPrincipal().getName();
        }
        else
        {
            return "anonymous";
        }
    }

    /////////////////////////////////////////////////////////////
    // Enqueue - a form service...
    /////////////////////////////////////////////////////////////

    @POST
    @Path("ji")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    public String enqueue(@FormParam("applicationname") String applicationName, @FormParam("module") String module,
            @FormParam("mail") String mail, @FormParam("keyword1") String keyword1, @FormParam("keyword2") String keyword2,
            @FormParam("keyword3") String keyword3, @FormParam("parentid") Integer parentId, @FormParam("user") String user,
            @FormParam("sessionid") String sessionId, @FormParam("parameterNames") List<String> prmNames,
            @FormParam("parameterValues") List<String> prmValues, @Context SecurityContext security)
    {
        if (user == null && security != null && security.getUserPrincipal() != null)
        {
            user = security.getUserPrincipal().getName();
        }

        JobRequest jd = JqmClientFactory.getClient().newJobRequest(applicationName, user);

        jd.setModule(module);
        jd.setEmail(mail);
        jd.setKeyword1(keyword1);
        jd.setKeyword2(keyword2);
        jd.setKeyword3(keyword3);
        jd.setParentID(parentId);
        jd.setSessionID(sessionId);

        for (int i = 0; i < prmNames.size(); i++)
        {
            String name = prmNames.get(i);
            String value = null;
            try
            {
                value = prmValues.get(i);
            }
            catch (IndexOutOfBoundsException e)
            {
                throw new ErrorDto("There should be as many parameter names as parameter values", 6, e, Status.BAD_REQUEST);
            }
            jd.addParameter(name, value);
            log.trace("Adding a parameter: " + name + " - " + value);
        }

        Integer i = jd.enqueue();
        return i.toString();
    }

    /////////////////////////////////////////////////////////////
    // Health
    /////////////////////////////////////////////////////////////

    @GET
    @Path("localnode/health")
    @Produces(MediaType.TEXT_PLAIN)
    public String getLocalNodeHealth() throws MalformedObjectNameException
    {
        // Local service only - not enabled when running on top of Tomcat & co.
        Node n = getLocalNodeIfRunningOnJqm();
        if (n == null)
        {
            throw new ErrorDto("can only retrieve local node health when the web app runs on top of JQM", "", 7, Status.BAD_REQUEST);
        }
        if (n.getJmxServerPort() == null || n.getJmxServerPort() == 0)
        {
            throw new ErrorDto("JMX is not enabled on this server", "", 8, Status.BAD_REQUEST);
        }

        // Connect to JMX server
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName nodeBean = new ObjectName("com.enioka.jqm:type=Node,name=" + n.getName());
        Set<ObjectName> names = server.queryNames(nodeBean, null);
        if (names.isEmpty())
        {
            throw new ErrorDto("could not find the JMX Mbean of the local JQM node", "", 8, Status.INTERNAL_SERVER_ERROR);
        }
        ObjectName localNodeBean = names.iterator().next();

        // Query bean
        Object result;
        try
        {
            result = server.getAttribute(localNodeBean, "AllPollersPolling");
        }
        catch (Exception e)
        {
            throw new ErrorDto("Issue when querying JMX server", 12, e, Status.INTERNAL_SERVER_ERROR);
        }
        if (!(result instanceof Boolean))
        {
            throw new ErrorDto("JMX bean answered with wrong datatype - answer was " + result.toString(), "", 9,
                    Status.INTERNAL_SERVER_ERROR);
        }

        // Analyze output
        boolean res = (Boolean) result;
        if (!res)
        {
            throw new ErrorDto("JQM node has is not working as expected", "", 11, Status.SERVICE_UNAVAILABLE);
        }

        return "Pollers are polling";
    }
}
