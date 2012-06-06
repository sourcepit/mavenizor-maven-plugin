/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.maven.execution.MavenExecutionRequest;
import org.junit.Test;
import org.sourcepit.common.maven.testing.EmbeddedMavenEnvironmentTest;
import org.sourcepit.common.maven.testing.MavenExecutionResult2;
import org.sourcepit.common.testing.Environment;

public class MavenizorMojoTest extends EmbeddedMavenEnvironmentTest
{
   @Override
   protected File getTestEnvironmentSnapshotFile()
   {
      return new File(getEnvironment().getBuildDir().getParentFile(), "test-environment.xml");
   }

   @Override
   protected Environment newEnvironment()
   {
      return Environment.get("env-it.properties");
   }

   @Override
   protected ClassLoader getClassLoader()
   {
      return new ClassLoader(super.getClassLoader())
      {
         @Override
         public Enumeration<URL> getResources(String name) throws IOException
         {
            List<URL> urls = new ArrayList<URL>();
            Enumeration<URL> resources = super.getResources(name);
            while (resources.hasMoreElements())
            {
               URL url = (URL) resources.nextElement();
               if (!url.toExternalForm().contains("tycho"))
               {
                  urls.add(url);
               }
            }
            return Collections.enumeration(urls);
         }
      };
   }

   // @Test
   public void testFeature() throws Throwable
   {
      final File projectDir = getResource("projects/org.sourcepit.mavenizor.test.feature");
      final File pomFile = new File(projectDir, "pom.xml");
      assertTrue(pomFile.exists());

      Thread.currentThread().setContextClassLoader(getClassLoader());

      final MavenExecutionRequest request = newMavenExecutionRequest(pomFile, System.getProperties(), getEnvironment()
         .newProperties(), "clean", "package");

      final MavenExecutionResult2 result = execute(request);
      assertSuccess(result);
   }

   @Test
   public void testPlugin() throws Throwable
   {
      final File projectDir = getResource("projects/org.sourcepit.mavenized");
      final File pomFile = new File(projectDir, "pom.xml");
      assertTrue(pomFile.exists());

      Thread.currentThread().setContextClassLoader(getClassLoader());

      final MavenExecutionRequest request = newMavenExecutionRequest(pomFile, System.getProperties(), getEnvironment()
         .newProperties(), "clean", "package");

      final MavenExecutionResult2 result = execute(request);
      assertSuccess(result);
   }

   private static void assertSuccess(MavenExecutionResult2 result) throws Throwable
   {
      for (Throwable throwable : result.getExceptions())
      {
         throw throwable;
      }
   }
}
