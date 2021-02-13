package com.enioka.jqm.runner.java;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.enioka.jqm.jdbc.DbConn;
import com.enioka.jqm.model.GlobalParameter;
import com.enioka.jqm.model.JobInstance;

import org.apache.commons.io.FilenameUtils;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenUpdatePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LibraryResolverMaven
{
    private static Logger jqmlogger = LoggerFactory.getLogger(LibraryResolverMaven.class);

    private static List<String> REPO_LIST = null;
    private static String MAVEN_SETTINGS_CL = null;
    private static String MAVEN_SETTINGS_FILE = null;

    private String[] mavenRepos;
    private String mavenSettingsClPath;
    private String mavenSettingsFilePath;

    LibraryResolverMaven(DbConn cnx)
    {
        mavenRepos = GlobalParameter.getParameter(cnx, "mavenRepo", "http://repo1.maven.org/maven2/").split(",");
        mavenSettingsClPath = GlobalParameter.getParameter(cnx, "mavenSettingsCL", null);
        mavenSettingsFilePath = GlobalParameter.getParameter(cnx, "mavenSettingsFile", null);
    }

    URL[] resolve(JobInstance ji) throws JqmPayloadException
    {
        ConfigurableMavenResolverSystem resolver = getMavenResolver();

        try
        {
            return extractMavenResults(resolver.resolve(ji.getJD().getJarPath()).withTransitivity().asFile());
        }
        catch (JqmPayloadException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new JqmPayloadException("Could not resolve a Maven payload path", e);
        }
    }

    ConfigurableMavenResolverSystem getMavenResolver()
    {
        // Retrieve resolver configuration
        if (REPO_LIST == null)
        {
            REPO_LIST = new ArrayList<>(5);
            for (String gp : mavenRepos)
            {
                REPO_LIST.add(gp);
            }

            MAVEN_SETTINGS_CL = mavenSettingsClPath;
            MAVEN_SETTINGS_FILE = mavenSettingsFilePath;
        }

        // Resolver. Sadly Shrinkwrap uses CL hacks and we must take them into account here.
        ClassLoader currentCl = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(LibraryResolverMaven.class.getClassLoader()); // Will usually be the bundle CL.
        ConfigurableMavenResolverSystem resolver = Maven.configureResolver();

        // settings.xml?
        if (MAVEN_SETTINGS_CL != null && MAVEN_SETTINGS_FILE == null)
        {
            jqmlogger.trace("Custom settings file from class-path will be used: " + MAVEN_SETTINGS_CL);
            resolver.fromClassloaderResource(MAVEN_SETTINGS_CL);
        }
        if (MAVEN_SETTINGS_FILE != null)
        {
            jqmlogger.trace("Custom settings file from file system will be used: " + MAVEN_SETTINGS_FILE);
            resolver.fromFile(MAVEN_SETTINGS_FILE);
        }
        Thread.currentThread().setContextClassLoader(currentCl); // only now (needed for CL search)

        // Repositories to use.
        boolean withCentral = false;
        for (String repo : REPO_LIST)
        {
            if (repo.contains("repo1.maven.org"))
            {
                withCentral = true;
                jqmlogger.trace("Using Maven central as a Maven repository");
                continue;
            }
            jqmlogger.trace("Using Maven repository {}", repo);
            resolver = resolver
                    .withRemoteRepo(MavenRemoteRepositories.createRemoteRepository("repo" + Math.abs(repo.hashCode()), repo, "default")
                            .setUpdatePolicy(MavenUpdatePolicy.UPDATE_POLICY_NEVER));
        }
        resolver.withMavenCentralRepo(withCentral);
        return resolver;
    }

    URL[] extractMavenResults(File[] depFiles) throws JqmPayloadException
    {
        int size = 0;
        for (File artifact : depFiles)
        {
            if (!"pom".equals(FilenameUtils.getExtension(artifact.getName())))
            {
                size++;
            }
        }
        URL[] tmp = new URL[size];
        int i = 0;
        for (File artifact : depFiles)
        {
            if ("pom".equals(FilenameUtils.getExtension(artifact.getName())))
            {
                continue;
            }
            try
            {
                tmp[i] = artifact.toURI().toURL();
            }
            catch (MalformedURLException e)
            {
                throw new JqmPayloadException("Incorrect dependency in POM file", e);
            }
            i++;
        }

        return tmp;
    }
}
