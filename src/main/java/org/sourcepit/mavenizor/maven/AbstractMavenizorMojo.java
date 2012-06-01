/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.sourcepit.common.utils.lang.PipedException;
import org.sourcepit.guplex.Guplex;

public abstract class AbstractMavenizorMojo extends AbstractMojo
{
   /** @component */
   private Guplex guplex;

   public final void execute() throws MojoExecutionException, MojoFailureException
   {
      guplex.inject(this, true);

      try
      {
         doExecute();
      }
      catch (PipedException e)
      {
         e.adaptAndThrow(MojoExecutionException.class);
         e.adaptAndThrow(MojoFailureException.class);
         throw new MojoExecutionException(e.getCause().getMessage(), e.getCause());
      }
   }

   protected abstract void doExecute() throws PipedException;
}
