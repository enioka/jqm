package com.enioka.jqm.clusternode;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.enioka.jqm.engine.Helpers;
import com.enioka.jqm.engine.JqmInitError;
import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.Node;
import com.enioka.jqm.xml.XmlJobDefParser;

/**
 * A mode of deployment where a directory is scanned for job definitions. <br>
 * Only used in single-node Docker mode.
 */
class DirectoryScanner implements Runnable
{
    private static Logger jqmlogger = LoggerFactory.getLogger(DirectoryScanner.class);
    private static final int PERIOD_MS = 10000;

    private final File baseScanDirectory;
    private final Node node;

    private Semaphore loop = new Semaphore(0);
    private boolean run = true;
    private Thread localThread = null;

    DirectoryScanner(String path, Node node)
    {
        baseScanDirectory = new File(path);
        if (baseScanDirectory.exists() && !baseScanDirectory.isDirectory())
        {
            throw new JqmInitError("Cannot scan a file - need a directory. " + baseScanDirectory.getAbsolutePath());
        }
        if (!baseScanDirectory.exists() && !baseScanDirectory.mkdir())
        {
            throw new JqmInitError("Cannot create directory " + baseScanDirectory.getAbsolutePath());
        }
        this.node = node;
    }

    void stop()
    {
        jqmlogger.info("Directory scanner has received a stop request");
        if (this.run)
        {
            this.run = false;
            if (this.localThread != null)
            {
                this.localThread.interrupt();
            }
        }
    }

    void forceLoop()
    {
        this.loop.release(1);
    }

    @Override
    public void run()
    {
        Thread.currentThread().setName("DIRECTORY_SCANNER;polling directory;");
        jqmlogger.info("Start of the directory scanner");
        this.localThread = Thread.currentThread();

        while (true)
        {
            try
            {
                loop.tryAcquire(PERIOD_MS, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException e)
            {
                run = false;
            }
            if (!run || Thread.currentThread().isInterrupted())
            {
                break;
            }
            scan();
        }

        jqmlogger.info("End of the directory scanner");
    }

    private void scan()
    {
        for (File subDir : baseScanDirectory.listFiles())
        {
            if (!subDir.isDirectory() || !subDir.canExecute())
            {
                jqmlogger.warn("Ignoring non-executable-directory file in deployment scanner: " + subDir.getAbsolutePath());
                continue;
            }

            File xml = null;
            int fileCount = 0;
            outerloop: for (File subFile : subDir.listFiles())
            {
                fileCount++;
                if (subFile.getName().endsWith(".xml"))
                {
                    if (xml == null)
                    {
                        xml = subFile;
                    }
                    else
                    {
                        jqmlogger.warn(
                                "There must be a single deployment descriptor per single deplopyment unit: " + subDir.getAbsolutePath());
                        continue outerloop;
                    }
                }
            }

            if (xml == null)
            {
                if (fileCount > 0)
                {
                    jqmlogger.warn("Files are present, but no deployment descriptor inside " + subDir.getAbsolutePath());
                }
                continue;
            }

            // We have a deployment unit! Just deploy it.
            try
            {
                FileUtils.copyDirectory(subDir, new File(node.getRepo(), subDir.getName()));
            }
            catch (IOException e)
            {
                jqmlogger.error("Could not copy deployment unit to job definition repository", e);
                continue;
            }

            try
            {
                importDeploymentUnit(new File(node.getRepo(), new File(subDir.getName(), xml.getName()).getPath()));
            }
            catch (Exception e)
            {
                jqmlogger.error("Could not import deployment descriptor " + xml.getName(), e);
                continue;
            }

            // If here, correctly assimilated. Remove the directory content.
            try
            {
                FileUtils.cleanDirectory(subDir);
            }
            catch (IOException e)
            {
                jqmlogger.warn("Could not clean scanner directory " + subDir, e);
                continue;
            }

            jqmlogger.info("Deployment unit correctly processed: " + subDir.getName());
        }
    }

    private void importDeploymentUnit(File deploymentDescriptor)
    {
        try (DbConn cnx = Helpers.getNewDbSession())
        {
            // Target remapped path is relative to repository root.
            XmlJobDefParser.parse(deploymentDescriptor.getAbsolutePath(), cnx, deploymentDescriptor.getParentFile().getName());
        }
    }
}
