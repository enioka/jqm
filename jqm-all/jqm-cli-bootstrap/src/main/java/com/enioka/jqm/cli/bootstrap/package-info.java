/**
 * Special package which will be inside system loader, not the OSGi loader. This allows the program embedding the OSGi framework to access
 * the CLI running inside the OSGi framework without too many hacks.
 */
@org.osgi.annotation.bundle.Export
@org.osgi.annotation.versioning.Version("1.0.0")
package com.enioka.jqm.cli.bootstrap;
