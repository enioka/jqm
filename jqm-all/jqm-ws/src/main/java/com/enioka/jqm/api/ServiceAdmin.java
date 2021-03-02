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
package com.enioka.jqm.api;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

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
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

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
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobDef;
import com.enioka.jqm.model.RPermission;
import com.enioka.jqm.model.RRole;
import com.enioka.jqm.model.RUser;
import com.enioka.jqm.pki.JdbcCa;
import com.enioka.jqm.tools.JqmXmlException;
import com.enioka.jqm.tools.XmlJobDefExporter;

@Path("/admin")
public class ServiceAdmin
{

    ///////////////////////////////////////////////////////////////////////////
    // Nodes
    ///////////////////////////////////////////////////////////////////////////

    @GET
    @Path("node")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache("public, max-age=60")
    public List<NodeDto> getNodes()
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getNodes(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("node/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache("public, max-age=60")
    public NodeDto getNode(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getNode(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("node")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setNodes(List<NodeDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncNodes(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @POST
    @Path("node")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setNode(NodeDto dto)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertNode(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getQueues(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("q")
    @Consumes(MediaType.APPLICATION_JSON)
    @HttpCache
    public void setQueues(List<QueueDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncQueues(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("q/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public QueueDto getQueue(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getQueue(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertQueue(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("q/{id}")
    public void deleteQueue(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteQueue(cnx, id);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getQueueMappings(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("qmapping/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public QueueMappingDto getQueueMapping(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getQueueMapping(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("qmapping")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setQueueMappings(List<QueueMappingDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncQueueMappings(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertQueueMapping(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("qmapping/{id}")
    public void deleteQueueMapping(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteQueueMapping(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getJndiObjectResource(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("jndi")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJndiResources(List<JndiObjectResourceDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncJndiObjectResource(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("jndi/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public JndiObjectResourceDto getJndiResource(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getJndiObjectResource(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertJndiObjectResource(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("jndi/{id}")
    public void deleteJndiResource(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteJndiObjectResource(cnx, id);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getGlobalParameter(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("prm")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setGlobalParameters(List<GlobalParameterDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncGlobalParameters(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("prm/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public GlobalParameterDto getGlobalParameter(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getGlobalParameter(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertGlobalParameter(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("prm/{id}")
    public void deleteGlobalParameter(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteGlobalParameter(cnx, id);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getJobDef(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("jd")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setJobDefs(List<JobDefDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncJobDefs(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("jd/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public JobDefDto getJobDef(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getJobDef(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertJobDef(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("jd/{id}")
    public void deleteJobDef(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteJobDef(cnx, id);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("jd/{id}/xml")
    @Produces(MediaType.APPLICATION_XML)
    @HttpCache
    public Response getJobDefDeploymentDescriptor(@PathParam("id") final int id)
    {
        StreamingOutput output = new StreamingOutput()
        {
                @Override
                public void write(OutputStream outputStream) throws WebApplicationException
                {
                    DbConn cnx = null;
                    try
                    {
                        cnx = Helpers.getDbSession();
                        XmlJobDefExporter.export(outputStream, JobDef.select(cnx, "jd_select_by_id", id), cnx);
                    }
                    catch (JqmXmlException e)
                    {
                        throw new JqmClientException(e);
                    }
                    finally
                    {
                        Helpers.closeQuietly(cnx);
                    }
                }
        };
        return Response.ok(output)
            .header("Content-Disposition", "attachment; filename=jobdef_" + id + ".xml")
            .build();
    }

    @GET
    @Path("jd/all/xml")
    @Produces(MediaType.APPLICATION_XML)
    @HttpCache
    public Response getJobDefDeploymentDescriptor()
    {
        StreamingOutput output = new StreamingOutput()
        {
            @Override
            public void write(OutputStream outputStream) throws WebApplicationException
            {
                DbConn cnx = null;
                try
                {
                    cnx = Helpers.getDbSession();
                    XmlJobDefExporter.export(outputStream, JobDef.select(cnx, "jd_select_all"), cnx);
                }
                catch (JqmXmlException e)
                {
                    throw new JqmClientException(e);
                }
                finally
                {
                    Helpers.closeQuietly(cnx);
                }
            }
        };
        return Response.ok(output)
            .header("Content-Disposition", "attachment; filename=jobdef.xml")
            .build();
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getUsers(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("user")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setUsers(List<RUserDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncUsers(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("user/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public RUserDto getUser(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getUser(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertUser(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("user/{id}")
    public void deleteUser(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteUser(cnx, id);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getRoles(cnx);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @PUT
    @Path("role")
    @Consumes(MediaType.APPLICATION_JSON)
    public void setRoles(List<RRoleDto> dtos)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.syncRoles(cnx, dtos);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Path("role/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @HttpCache
    public RRoleDto getRole(@PathParam("id") int id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            return MetaService.getRole(cnx, id);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
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
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.upsertRole(cnx, dto);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @DELETE
    @Path("role/{id}")
    public void deleteRole(@PathParam("id") Integer id)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            MetaService.deleteRole(cnx, id, false);
            cnx.commit();
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("me")
    @HttpCache("private, max-age=36000")
    public PemissionsBagDto getMyself(@Context HttpServletRequest req)
    {
        List<String> res = new ArrayList<String>();
        PemissionsBagDto b = new PemissionsBagDto();

        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
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
        finally
        {
            Helpers.closeQuietly(cnx);
        }

        b.permissions = res;
        return b;
    }

    @Path("user/{id}/certificate")
    @Produces("application/zip")
    @GET
    public InputStream getNewCertificate(@PathParam("id") int userId)
    {
        DbConn cnx = null;
        try
        {
            cnx = Helpers.getDbSession();
            RUser u = RUser.select_id(cnx, userId);
            return JdbcCa.getClientData(cnx, u.getLogin());
        }
        catch (Exception e)
        {
            throw new ErrorDto("could not create certificate", 5, e, Status.INTERNAL_SERVER_ERROR);
        }
        finally
        {
            Helpers.closeQuietly(cnx);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Engine log
    //////////////////////////////////////////////////////////////////////////

    @Path("node/{nodeName}/log")
    @Produces("application/octet-stream")
    @GET
    public InputStream getNodeLog(@PathParam("nodeName") String nodeName, @QueryParam("latest") int latest,
            @Context HttpServletResponse res)
    {
        SelfDestructFileStream fs = (SelfDestructFileStream) ((JdbcClient) JqmClientFactory.getClient()).getEngineLog(nodeName, latest);
        res.setHeader("Content-Disposition", "attachment; filename=" + nodeName + ".log");
        return fs;
    }
}
