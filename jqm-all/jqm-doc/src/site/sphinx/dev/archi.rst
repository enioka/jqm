Software architecture
#############################

Global layout of the JQM engine
***********************************

JQM implements a multi-layered plugin architecture.

Plugin means here that functions which by nature have multiple implementations must be isolated in their own plugin (or module) and accessed through a common interface.
This allows JQM to naturally adapt to new requirements with simply adding modules, rather than having to modify existing ones.

Multi-layered means that inside modules a structure must be respected for easier code maintenance. Those layers are:

* Model
* DAL/repository - the layer responsible for JDBC communication with the database
* Services, implementing the business requirements. (the bulk of the JQM code)

The communication between layers is done through direct manipulation of instances which are created directly (no injection) or through the use of the injection of interfaces at the frontier of extension points.

Great care is taken to expose minimal APIs either to the general public or to other JQM modules.

Plugin system
******************

Principles
===============

JQM uses the OSGi specification (implemented by the Apache Felix Framework but only spec-compliant APIs are used) to define its plugins.

This means that extensions points are defined by:

* a normal Java interface
* the use of one or many implementations of the interface exposed as OSGi declarative services (scr).

Only annotations are allowed to define services in code. Activators are frowned upon.

Other than that, public packages are signaled by a decorator inside package-info.java, which is compulsory for every package in the JQM code with at least a javadoc.

Please note that OSGi has a lot of other functionalities. They are not used
For JQM, OSGi is a static plugin system and an API surface control system. Nothing more.
Particularly, OSGi offers "hot reload" or "hot patching" of programs - we do not care about this at all in an asynchronous system which can be restarted easily.

Finally, extension points APIs muste be treated as if they were public APIs. This is not the case, but is compulsory for lowering the overall cost of maintenance
(it is a lot better when plugins do not have to be rewritten every JQM version...)

.. warning:: JQM plugin system is internal. It is not meant for third party plugins. If the JQM maintainers want to break ascending compatibility in the plugin APIs, they can do so without notice.

.. note:: OSGi does not leak outside JQM. OSGi has no influence on the jobs themselves - they still have the same non-OSGi class loader as described in the dedicated section.

Extension points
==================

Current extension points

* runners, implementing the `JobRunner` API. They are responsible for actually launching a job instance. For example, the shell runner is able to launch shell command lines.
* database adapters, implementing the `DbAdapter` API. They are responsible for everything which is specific to a given database (adapt SQL, migration scripts...)
* resource schedulers, responsible for the management of a single resource type that can be used in the queuing algorithms (tokens, RAM availability, etc)

Future extension points:

* queuing algorithm.

Actually not an extension point but one could be fooled:

* Implementations of the client API. We have two and only two, with no hope for more: JDBC and web service/Jersey. Also, these are used by other programs and therefore must be used in a normal non-OSGi context.
* Providers - these are simply a set of JNDI resource providers which are loaded by job through normal Java APIs.
* Handlers - hum. Well. Now we think about these...


Layers
**************

Data access
============

`jqm-model` artifact: contains

* the model classes, used throughout JQM
* a technical layer designed to allow running predefined SQL queries against relational databases. It relies on plugins to adapt the SQL queries to specific database engines.
* all the SQL queries ever done in the engine

The different database plugins are each inside their own artifact, which are sub projects of `jqm-dbadapter`:

* jqm-impl-db2
* jqm-impl-hsql
* jqm-impl-mysql
* jqm-impl-mysql8
* jqm-impl-oracle
* jqm-impl-pg

Usually all the plugins have to take care is the availability of sequences, the time types, the possibility to bind arrays with simple string search and replace.
JQM only uses very straightforward SQL queries, which allows this very simple method of handling compatibility with multiple database engines.
Also, as "adapted" SQL queries can be cached, this is a very fast mechanism.

.. warning:: JQM used to have a very detrimental ORM, traces remain and should be cleaned with time.

The model classes contain the mapping methods needed to construct themselves from a JDBC `ResultSet` and to persist unitarily. Since the engine mostly deals with independent rows,
this means there is no need for a higher level repository which would handle ensemblist operations on the database.
However, this is not true for the administration GUI which does such operations. Therefore, a `jqm-admin` artifact exists in order to provide repositories for administration operations.
It is however the most sorry part of the JQM and cleaning it up is a goal of future JQM versions with already open PRs.

There is one exception to the "all SQL is inside `jqm-model`" principle. The Query API allows to construct arbitrary queries on `History` rows and therefore creates dynamic SQL queries.
This code is not inside `jqm-model` but directly inside the client implementation, and then run like any other query through the DB abstraction technical layer.

Engine core
===============

The engine is responsible for deciding which `JobRequest` should run through a queuing algorithm, mark them, send them to a `JqmRunner` and update the status of the request during execution and after its end.
It is basically the very core of JQM. It is implemented inside `jqm-engine`, with a hierarchical structure:

* the `JqmEngine` is the root of the hierarchy, there is one per running JQM node (usually only one, multiples during some automated tests).

  * it is responsible for the initialization of the engine, such as checking configuration
  * responsible for starting all the helpers, such as the JMX registry, the "check orders given to the engine" poller.
  * owns the one and only `QueuePoller` which is responsible for actually polling all the queues associated to the current engine.

    * the poller actually delegates how to poll to plugins - default method "mostly FIFO" is described in a separate document.
    * the poller checks if resources are available
    * the poller delegates execution to `JqmRunner` instances (plugins).

Cluster node
================

An engine is simply an instance of `JqmEngine` started with a given configuration. That configuration is provided by `jqm-clusternode` which provides the configuration and the needed plugins.
It also starts a web server for each node.

It is basically the "standard distribution" of the JQM engine.

It is exposed through start/stop verbs of the `jqm-cli` project, which is the entry point to everything JQM.

Finally, there is a last artifact important here: `jqm-service` which provides the OSGi framework in which to run everything else. This is the packaging project, producing the standard distribution zip file.

Web API & GUI
****************

JQM uses a simple MVC model.

JQM has three set of web service APIs.

* the client API, with the full set of verbs needed to create and manage job instances. This is the main API used in other systems.
* the administration API, a private API made solely for the sake of the administration GUI
* the simple API, a minimal set of web services needed by the CLI interface and for file retrievals

The model part is made of the already public DTOs of the client API (and of the internal admin API).

The controller part is implemented by standard JAX-RS services.

The view part is implemented inside a ReactJS client.


In terms of hosting, everything is hosted by the Apache Felix implementation of the OSGi HTTP/Rest whiteboard.
Which is basically an OSGi-compatible version of Jetty.
This will allow in the future to extend web services and UI inside OSGi plugins.

