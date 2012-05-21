/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.eclipse.osgi.service.resolver.State;
import org.slf4j.Logger;
import org.sourcepit.common.utils.lang.PipedException;

/**
 * @requiresDependencyResolution compile
 * @goal mavenize
 * @phase package
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorMojo extends AbstractMavenizorMojo
{
   @Inject
   private Logger logger;

   /** @parameter expression="${session}" */
   private MavenSession session;

   @Inject
   private BundleResolver bundleResolver;

   @Override
   protected void doExecute() throws PipedException
   {
      logger.info("Hello :-)");

      final OsgiStateBuilder stateBuilder = new OsgiStateBuilder();

      bundleResolver.resolve(session, new BundleResolver.Handler()
      {
         public void resolved(File bundleLocation)
         {
            stateBuilder.addBundle(bundleLocation);
         }
      });

      final State state = stateBuilder.getState();

      logger.info("awesome!");
   }
}
