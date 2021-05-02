package com.enioka.jqm.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.enioka.jqm.client.api.JqmClient;
import com.enioka.jqm.model.JobDefParameter;
import com.enioka.jqm.model.JobDef.PathType;

/**
 * Helper class to define a new payload. This will help create an actual job definition, as if it had been imported from an XML deployment
 * descriptor.<br>
 * There are two possibilities to create a job definition: from a class inside the current class path, or from an external Jar.
 */
public class TestJobDefinition
{
    // Identity
    String description;
    String name;

    // Where the payload is
    String javaClassName;
    String path = "/dev/null";
    PathType pathType = PathType.MEMORY;

    // How it should run
    String queueName;
    boolean highlander = false;

    // Parameters
    Map<String, String> parameters = new HashMap<>();

    // Optional classifiers
    String application, module, keyword1, keyword2, keyword3;

    // Class loading options
    String specificIsolationContext;
    boolean childFirstClassLoader = false;
    List<String> hiddenJavaClasses = new ArrayList<>();
    boolean classLoaderTracing = false;

    ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTION
    ///////////////////////////////////////////////////////////////////////////

    private TestJobDefinition()
    {}

    /**
     * This creates a job definition from a given class. This class must be present inside the current class path, and will be loaded by the
     * engine with the current class loader. This means that most specific class loading option (child first, ...) are mostly useless in
     * this case.
     *
     * @param name
     *            the name to give the new job definition. This is the name that is later reused in client APIs, such as
     *            {@link JqmClient#enqueue(String, String)}.
     * @param description
     *            a free text describing the job definition
     * @param testedClass
     *            the class containing the job to run.
     * @return the object itself (fluid API)
     */
    public static TestJobDefinition createFromClassPath(String name, String description, Class<? extends Object> testedClass)
    {
        TestJobDefinition res = new TestJobDefinition();
        res.name = name;
        res.description = description;
        res.javaClassName = testedClass.getCanonicalName();
        res.path = "/dev/null";
        res.pathType = PathType.MEMORY;
        return res;
    }

    /**
     * Create a job definition from a class inside an existing jar file. This creates the exact replica of what happens inside a production
     * JQM cluster, including taking into account all the various class loading options (child first, external library directory...).
     *
     * @param name
     *            the name to give the new job definition. This is the name that is later reused in client APIs, such as
     *            {@link JqmClient#enqueue(String, String)}.
     * @param description
     *            a free text describing the job definition
     * @param testedClassCanonicalName
     *            the class containing the job to run (full canonical name, i.e. including package name)
     * @param jarPath
     *            path to the jar file, relative to the current directory.
     * @return
     */
    public static TestJobDefinition createFromJar(String name, String description, String testedClassCanonicalName, String jarPath)
    {
        TestJobDefinition res = new TestJobDefinition();
        res.name = name;
        res.description = description;
        res.javaClassName = testedClassCanonicalName;
        res.path = jarPath;
        res.pathType = PathType.FS;
        return res;
    }

    ///////////////////////////////////////////////////////////////////////////
    // FLUID API
    ///////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////
    // Compulsory parameters

    String getDescription()
    {
        return description;
    }

    void setDescription(String description)
    {
        this.description = description;
    }

    String getName()
    {
        return name;
    }

    void setName(String name)
    {
        this.name = name;
    }

    String getJavaClassName()
    {
        return javaClassName;
    }

    void setJavaClassName(String javaClassName)
    {
        this.javaClassName = javaClassName;
    }

    String getPath()
    {
        return path;
    }

    void setPath(String path)
    {
        this.path = path;
    }

    PathType getPathType()
    {
        return pathType;
    }

    void setPathType(PathType pathType)
    {
        this.pathType = pathType;
    }

    String getQueueName()
    {
        return queueName;
    }

    // end compulsory parameters
    ///////////////////////////////////////////////////////////////////////////

    /**
     * The jobs will run on this queue. If no set (or set to null) will run on the default queue.
     *
     * @param queueName
     */
    public TestJobDefinition setQueueName(String queueName)
    {
        this.queueName = queueName;
        return this;
    }

    boolean isHighlander()
    {
        return highlander;
    }

    /**
     * This enables Highlander mode. See the documentation on Highlander jobs.
     */
    public TestJobDefinition enableHighlander()
    {
        this.highlander = true;
        return this;
    }

    List<JobDefParameter> getParameters()
    {
        List<JobDefParameter> prms = new ArrayList<>(this.parameters.size());
        for (Map.Entry<String, String> e : this.parameters.entrySet())
        {
            JobDefParameter prm = new JobDefParameter();
            prm.setKey(e.getKey());
            prm.setValue(e.getValue());
            prms.add(prm);
        }
        return prms;
    }

    /**
     * Add a parameter.
     *
     * @param key
     *            must be unique
     * @param value
     */
    public TestJobDefinition addParameter(String key, String value)
    {
        this.parameters.put(key, value);
        return this;
    }

    String getApplication()
    {
        return application;
    }

    /**
     * An optional classifier.
     *
     * @param application
     */
    public TestJobDefinition setApplication(String application)
    {
        this.application = application;
        return this;
    }

    String getModule()
    {
        return module;
    }

    /**
     * An optional classifier.
     *
     * @param module
     */
    public TestJobDefinition setModule(String module)
    {
        this.module = module;
        return this;
    }

    String getKeyword1()
    {
        return keyword1;
    }

    /**
     * An optional classifier.
     *
     * @param keyword1
     */
    public TestJobDefinition setKeyword1(String keyword1)
    {
        this.keyword1 = keyword1;
        return this;
    }

    String getKeyword2()
    {
        return keyword2;
    }

    /**
     * An optional classifier.
     *
     * @param keyword2
     */
    public TestJobDefinition setKeyword2(String keyword2)
    {
        this.keyword2 = keyword2;
        return this;
    }

    String getKeyword3()
    {
        return keyword3;
    }

    /**
     * An optional classifier.
     *
     * @param keyword3
     */
    public TestJobDefinition setKeyword3(String keyword3)
    {
        this.keyword3 = keyword3;
        return this;
    }

    String getSpecificIsolationContext()
    {
        return specificIsolationContext;
    }

    /**
     * By default jobs run inside a dedicated class loader thrown out after the run. By setting this to a non null value, this job will run
     * inside a re-used class loader. All jobs using the same specificIsolationContext share the same class loader. See documentation on
     * class loading inside JQM.
     *
     * @param specificIsolationContext
     *            name of the class loader to use or null to use default behaviour.
     */
    public TestJobDefinition setSpecificIsolationContext(String specificIsolationContext)
    {
        this.specificIsolationContext = specificIsolationContext;
        return this;
    }

    boolean isChildFirstClassLoader()
    {
        return childFirstClassLoader;
    }

    /**
     * This enables child-first class loading.<br>
     * Note that if the payload is actually inside your unit test class path, this won't do much. See documentation on class loading inside
     * JQM.
     *
     * @param childFirstClassLoader
     */
    public TestJobDefinition setChildFirstClassLoader(boolean childFirstClassLoader)
    {
        this.childFirstClassLoader = childFirstClassLoader;
        return this;
    }

    String getHiddenJavaClasses()
    {
        String res = "";
        for (String s : hiddenJavaClasses)
        {
            res += s + ",";
        }
        if (hiddenJavaClasses.size() > 0)
        {
            return res.substring(0, res.length() - 2);
        }
        return "";
    }

    /**
     * This prevents the given class from being loaded by a parent class loader. Id es: if the class is not directly available to the
     * payload (inside the payload libraries for example) it will not be loaded.
     *
     * @param canonicalClassName
     */
    public TestJobDefinition addHiddenJavaClasses(String canonicalClassName)
    {
        this.hiddenJavaClasses.add(canonicalClassName);
        return this;
    }

    boolean isClassLoaderTracing()
    {
        return classLoaderTracing;
    }

    /**
     * Enable verbose class loading fot his payload.
     */
    public TestJobDefinition enableClassLoaderTracing()
    {
        this.classLoaderTracing = true;
        return this;
    }

}
