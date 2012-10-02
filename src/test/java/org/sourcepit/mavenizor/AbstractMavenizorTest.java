/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.sourcepit.common.testing.Environment;
import org.sourcepit.common.testing.Workspace;
import org.sourcepit.guplex.test.GuplexTest;

public abstract class AbstractMavenizorTest extends GuplexTest
{
   private final Environment env = newEnvironment();

   @Rule
   public Workspace ws = newWorkspace();

   @Override
   protected boolean isUseIndex()
   {
      return true;
   }

   protected Workspace newWorkspace()
   {
      return new Workspace(new File(env.getBuildDir(), "ws"), false);
   }

   protected Environment newEnvironment()
   {
      return Environment.get("env-tests.properties");
   }

   public Environment getEnvironment()
   {
      return env;
   }

   protected Workspace getWs()
   {
      return ws;
   }

   protected File getResource(String path) throws IOException
   {
      File resources = getResourcesDir();
      File resource = new File(resources, path).getCanonicalFile();
      return ws.importFileOrDir(resource);
   }

   protected File getResourcesDir()
   {
      return getEnvironment().getResourcesDir();
   }
}
