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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import com.enioka.admin.MetaService;
import com.enioka.api.admin.GlobalParameterDto;
import com.enioka.api.admin.JndiObjectResourceDto;
import com.enioka.api.admin.JobDefDto;
import com.enioka.api.admin.NodeDto;
import com.enioka.api.admin.PemissionsBagDto;
import com.enioka.api.admin.QueueDto;
import com.enioka.api.admin.QueueMappingDto;
import com.enioka.api.admin.RRoleDto;
import com.enioka.api.admin.RUserDto;
import com.enioka.jqm.client.api.JqmClientException;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.RPermission;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.RUser;
import com.enioka.jqm.pki.JdbcCa;
import com.enioka.jqm.ws.plumbing.HttpCache;
import com.enioka.jqm.xml.JqmXmlException;
import com.enioka.jqm.xml.XmlJobDefExporter;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/admin")
@Component(service = ServiceAdmin.class, configurationPolicy = ConfigurationPolicy.REQUIRE, scope = ServiceScope.SINGLETON)
@JaxrsResource
public class ServiceAdmin
{
    static Logger log = LoggerFactory.getLogger(ServiceAdmin.class);

    @Activate
    public void onServiceActivation(Map<String, Object> properties)
    {
        log.info("\tStarting ServiceAdmin");
    }

    ///////////////////////////////////////////////////////////////////////////
    // Nodes
    ///////////////////////////////////////////////////////////////////////////

    @GET
    @Path("node")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache("public, max-age=60")
    public List<NodeDto> getNodes()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getNodes(cnx);
        }
    }

    @GET
    @Path("node/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache("public, max-age=60")
    public NodeDto getNode(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getNode(cnx, id);
        }
    }

    @PUT
    @Path("node")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setNodes(List<NodeDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncNodes(cnx, dtos);
            cnx.commit();
        }
    }

    @POST
    @Path("node")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setNode(NodeDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertNode(cnx, dto);
            cnx.commit();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Queues
    ///////////////////////////////////////////////////////////////////////////

    @GET
    @Path("q")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<QueueDto> getQueues()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getQueues(cnx);
        }
    }

    @PUT
    @Path("q")
    @Consumes(MediaType.APPLICATION_JSON)
    @HttpCache
    public void setQueues(List<QueueDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncQueues(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("q/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public QueueDto getQueue(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getQueue(cnx, id);
        }
    }

    @PUT
    @Path("q/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueue(@PathParam("id") Integer id, QueueDto dto)
    {
        dto.setId(id);
        setQueue(dto);
    }

    @POST
    @Path("q")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueue(QueueDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertQueue(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("q/{id}")
    public void deleteQueue(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteQueue(cnx, id);
            cnx.commit();
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Deployment parameters - queue mappings
    ///////////////////////////////////////////////////////////////////////////

    @GET
    @Path("qmapping")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<QueueMappingDto> getQueueMappings()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getQueueMappings(cnx);
        }
    }

    @GET
    @Path("qmapping/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public QueueMappingDto getQueueMapping(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getQueueMapping(cnx, id);
        }
    }

    @PUT
    @Path("qmapping")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueueMappings(List<QueueMappingDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncQueueMappings(cnx, dtos);
            cnx.commit();
        }
    }

    @PUT
    @Path("qmapping/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueueMapping(@PathParam("id") Integer id, QueueMappingDto dto)
    {
        dto.setId(id);
        setQueueMapping(dto);
    }

    @POST
    @Path("qmapping")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueueMapping(QueueMappingDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertQueueMapping(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("qmapping/{id}")
    public void deleteQueueMapping(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteQueueMapping(cnx, id);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // JNDI
    //////////////////////////////////////////////////////////////////////////

    @GET
    @Path("jndi")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<JndiObjectResourceDto> getJndiResources()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getJndiObjectResource(cnx);
        }
    }

    @PUT
    @Path("jndi")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJndiResources(List<JndiObjectResourceDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncJndiObjectResource(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("jndi/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public JndiObjectResourceDto getJndiResource(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getJndiObjectResource(cnx, id);
        }
    }

    @PUT
    @Path("jndi/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJndiResource(@PathParam("id") Integer id, JndiObjectResourceDto dto)
    {
        dto.setId(id);
        setJndiResource(dto);
    }

    @POST
    @Path("jndi")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJndiResource(JndiObjectResourceDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertJndiObjectResource(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("jndi/{id}")
    public void deleteJndiResource(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteJndiObjectResource(cnx, id);
            cnx.commit();
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Global parameters
    //////////////////////////////////////////////////////////////////////////

    @GET
    @Path("prm")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<GlobalParameterDto> getGlobalParameters()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getGlobalParameter(cnx);
        }
    }

    @PUT
    @Path("prm")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setGlobalParameters(List<GlobalParameterDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncGlobalParameters(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("prm/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public GlobalParameterDto getGlobalParameter(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getGlobalParameter(cnx, id);
        }
    }

    @PUT
    @Path("prm/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setGlobalParameter(@PathParam("id") Integer id, GlobalParameterDto dto)
    {
        dto.setId(id);
        setGlobalParameter(dto);
    }

    @POST
    @Path("prm")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setGlobalParameter(GlobalParameterDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertGlobalParameter(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("prm/{id}")
    public void deleteGlobalParameter(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteGlobalParameter(cnx, id);
            cnx.commit();
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // JobDef
    //////////////////////////////////////////////////////////////////////////

    @GET
    @Path("jd")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<JobDefDto> getJobDefs()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getJobDef(cnx);
        }
    }

    @PUT
    @Path("jd")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobDefs(List<JobDefDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncJobDefs(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("jd/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public JobDefDto getJobDef(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getJobDef(cnx, id);
        }
    }

    @PUT
    @Path("jd/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobDef(@PathParam("id") Integer id, JobDefDto dto)
    {
        dto.setId(id);
        setJobDef(dto);
    }

    @POST
    @Path("jd")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobDef(JobDefDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertJobDef(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("jd/{id}")
    public void deleteJobDef(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteJobDef(cnx, id);
            cnx.commit();
        }
    }

    @GET
    @Path("jd/{id}/xml")
    @Produces(MediaType.APPLICATION_XML)
    @HttpCache
    public void getJobDefDeploymentDescriptor(@PathParam("id") int id, @Context HttpServletResponse res)
    {
        res.setHeader("Content-Disposition", "attachment; filename=jobdef_" + id + ".xml");

        try (DbConn cnx = Helpers.getDbSession())
        {
            XmlJobDefExporter.export(res.getOutputStream(), JobDef.select(cnx, "jd_select_by_id", id), cnx);
        }
        catch (JqmXmlException e)
        {
            throw new JqmClientException(e);
        }
        catch (IOException e)
        {
            throw new JqmClientException(e);
        }
    }

    @GET
    @Path("jd/all/xml")
    @Produces(MediaType.APPLICATION_XML)
    @HttpCache
    public void getJobDefDeploymentDescriptor(@Context HttpServletResponse res)
    {
        res.setHeader("Content-Disposition", "attachment; filename=jobdef.xml");

        try (DbConn cnx = Helpers.getDbSession())
        {
            XmlJobDefExporter.export(res.getOutputStream(), JobDef.select(cnx, "jd_select_all"), cnx);
        }
        catch (JqmXmlException e)
        {
            throw new JqmClientException(e);
        }
        catch (IOException e)
        {
            throw new JqmClientException(e);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // User
    //////////////////////////////////////////////////////////////////////////

    @GET
    @Path("user")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<RUserDto> getUsers()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getUsers(cnx);
        }
    }

    @PUT
    @Path("user")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setUsers(List<RUserDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncUsers(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("user/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public RUserDto getUser(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getUser(cnx, id);
        }
    }

    @PUT
    @Path("user/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setUser(@PathParam("id") Integer id, RUserDto dto)
    {
        dto.setId(id);
        setUser(dto);
    }

    @POST
    @Path("user")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setUser(RUserDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertUser(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("user/{id}")
    public void deleteUser(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteUser(cnx, id);
            cnx.commit();
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Role
    // ////////////////////////////////////////////////////////////////////////

    @GET
    @Path("role")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public List<RRoleDto> getRoles()
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getRoles(cnx);
        }
    }

    @PUT
    @Path("role")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setRoles(List<RRoleDto> dtos)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.syncRoles(cnx, dtos);
            cnx.commit();
        }
    }

    @GET
    @Path("role/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public RRoleDto getRole(@PathParam("id") int id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            return MetaService.getRole(cnx, id);
        }
    }

    @PUT
    @Path("role/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setRole(@PathParam("id") Integer id, RRoleDto dto)
    {
        dto.setId(id);
        setRole(dto);
    }

    @POST
    @Path("role")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setRole(RRoleDto dto)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.upsertRole(cnx, dto);
            cnx.commit();
        }
    }

    @DELETE
    @Path("role/{id}")
    public void deleteRole(@PathParam("id") Integer id)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            MetaService.deleteRole(cnx, id, false);
            cnx.commit();
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("me")
    @HttpCache("private, max-age=36000")
    public PemissionsBagDto getMyself(@Context HttpServletRequest req)
    {
        List<String> res = new ArrayList<>();
        PemissionsBagDto b = new PemissionsBagDto();

        try (DbConn cnx = Helpers.getDbSession())
        {
            String auth = GlobalParameter.getParameter(cnx, "enableWsApiAuth", "true");
            if (auth.equals("false"))
            {
                res.add("*:*");
                b.enforced = false;
            }
            else
            {
                RUser memyselfandi = RUser.selectlogin(cnx, req.getUserPrincipal().getName());
                b.enforced = true;
                b.login = memyselfandi.getLogin();
                for (RRole r : memyselfandi.getRoles(cnx))
                {
                    for (RPermission p : r.getPermissions(cnx))
                    {
                        res.add(p.getName());
                    }
                }
            }
        }

        b.permissions = res;
        return b;
    }

    @Path("user/{id}/certificate")
    @Produces("application/zip")
    @GET
    public InputStream getNewCertificate(@PathParam("id") int userId)
    {
        try (DbConn cnx = Helpers.getDbSession())
        {
            RUser u = RUser.select_id(cnx, userId);
            return JdbcCa.getClientData(cnx, u.getLogin());
        }
        catch (Exception e)
        {
            throw new ErrorDto("could not create certificate", 5, e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Engine log
    //////////////////////////////////////////////////////////////////////////

    @Path("node/{nodeName}/log")
    @Produces("application/octet-stream")
    @GET
    public InputStream SS(@PathParam("nodeName") String nodeName, @QueryParam("latest") int latest, @Context HttpServletResponse res)
    {
        // TODO
        /*
         * InputStream fs = ((JdbcClient) JqmClientFactory.getClient()).getEngineLog(nodeName, latest); res.setHeader("Content-Disposition",
         * "attachment; filename=" + nodeName + ".log"); return fs;
         */

        return null;
    }
}
