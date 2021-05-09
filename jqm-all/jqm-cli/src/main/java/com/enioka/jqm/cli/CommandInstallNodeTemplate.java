package com.enioka.jqm.cli;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.enioka.admin.MetaService;
import com.enioka.api.admin.NodeDto;
import com.enioka.api.admin.QueueMappingDto;
import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.jdbc.DbConn;

@Parameters(commandNames = "Install-NodeTemplate", commandDescription = "Apply a template, i.e. the configuration of a node, to another node.")
class CommandInstallNodeTemplate extends CommandBase
{
    @Parameter(names = { "-t", "--template" }, description = "Name of the template to apply (i.e. node to copy from).", required = true)
    private String templateName;

    @Parameter(names = { "-n", "--node" }, description = "Name of the target node (i.e. node to copy to).", required = true)
    private String nodeName;

    @Override
    int doWork()
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            // Throws exception if nodes not found.
            NodeDto template = MetaService.getNode(cnx, templateName);
            NodeDto target = MetaService.getNode(cnx, nodeName);

            // Apply deployments parameters
            ArrayList<QueueMappingDto> mappings = new ArrayList<>(MetaService.getQueueMappings(cnx));
            List<QueueMappingDto> toRemove = new ArrayList<>(10);
            List<QueueMappingDto> toAdd = new ArrayList<>(10);
            for (QueueMappingDto mapping : mappings)
            {
                if (mapping.getNodeId().equals(template.getId()))
                {
                    QueueMappingDto r = new QueueMappingDto();
                    r.setEnabled(mapping.getEnabled());
                    r.setNbThread(mapping.getNbThread());
                    r.setNodeId(target.getId());
                    r.setNodeName(target.getName());
                    r.setPollingInterval(mapping.getPollingInterval());
                    r.setQueueId(mapping.getQueueId());
                    r.setQueueName(mapping.getQueueName());
                    toAdd.add(r);
                }
                if (mapping.getNodeId().equals(target.getId()))
                {
                    toRemove.add(mapping);
                }
            }

            mappings.addAll(toAdd);
            mappings.removeAll(toRemove);
            MetaService.syncQueueMappings(cnx, mappings);

            // Basic properties
            target.setEnabled(template.getEnabled());
            target.setJmxRegistryPort(template.getJmxRegistryPort());
            target.setJmxServerPort(template.getJmxServerPort());
            target.setJobRepoDirectory(template.getJobRepoDirectory());
            target.setLoadApiAdmin(template.getLoadApiAdmin());
            target.setLoadApiClient(template.getLoadApiClient());
            target.setLoapApiSimple(template.getLoapApiSimple());
            target.setOutputDirectory(template.getOutputDirectory());
            target.setDns(template.getDns());
            target.setPort(template.getPort());
            target.setRootLogLevel(template.getRootLogLevel());
            target.setTmpDirectory(template.getTmpDirectory());
            MetaService.upsertNode(cnx, target);

            // Done - meta service does not commit
            cnx.commit();
            return 0;
        }
    }
}
