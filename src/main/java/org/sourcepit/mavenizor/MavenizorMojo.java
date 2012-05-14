/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sourcepit.common.utils.lang.PipedException;

/**
 * @requiresDependencyResolution compile
 * @goal mavenize
 * @phase package
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorMojo extends AbstractMavenizorMojo
{
   private final static Logger LOG = LoggerFactory.getLogger(MavenizorMojo.class);

   @Override
   protected void doExecute() throws PipedException
   {
      LOG.info("Hello :-)");
   }
}
