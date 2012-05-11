/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sourcepit.guplex.Guplex;

/**
 * @requiresDependencyResolution compile
 * @goal mavenize
 * @phase package
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorMojo extends AbstractMojo
{
   private final static Logger LOG = LoggerFactory.getLogger(MavenizorMojo.class);

   /** @component */
   private Guplex guplex;

   public void execute() throws MojoExecutionException, MojoFailureException
   {
      guplex.inject(this, true);
      LOG.info("Hello :-)");
   }
}
