/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.apache.maven.execution.MavenExecutionRequest;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.junit.Test;
import org.sourcepit.common.maven.environment.EnvironmentSnapshot;
import org.sourcepit.common.maven.testing.EmbeddedMavenEnvironmentTest;
import org.sourcepit.common.maven.testing.MavenExecutionResult2;
import org.sourcepit.common.testing.Environment;
import org.sourcepit.common.utils.lang.Exceptions;

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

   EnvironmentSnapshot snapshot;

   @Override
   public void setUp() throws Exception
   {
      super.setUp();


      snapshot = newTestEnvironmentSnapshot();
   }

   @Override
   protected void customizeContainerConfiguration(ContainerConfiguration containerConfiguration)
   {
      super.customizeContainerConfiguration(containerConfiguration);

      ClassLoader load = new ClassLoader(getClassLoader())
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


      containerConfiguration.setClassWorld(new ClassWorld("plexus.core", load)
      {

         public synchronized org.codehaus.plexus.classworlds.realm.ClassRealm newRealm(String id,
            ClassLoader classLoader) throws org.codehaus.plexus.classworlds.realm.DuplicateRealmException
         {
            final ClassRealm newRealm = super.newRealm(id, classLoader);
            if (!id.equals("plexus.core"))
            {
               final ClassRealm realm;
               try
               {
                  realm = getRealm("plexus.core");
               }
               catch (NoSuchRealmException e)
               {
                  throw Exceptions.pipe(e);
               }

               for (String string : snapshot.getPackages())
               {
                  newRealm.importFrom(realm, string + ".*");
               }
               //
               // newRealm.importFrom(realm, "org.eclipse.tycho.artifacts.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.utils.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.osgitools.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.osgitools.project.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.osgitools.targetplatform.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.resolver.shared.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.compiler.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.maven.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.maven.utils.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.p2.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.resolver.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.launching.internal.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.launching.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.locking.facade.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.osgi.configuration.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.core.facade.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.facade.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.facade.internal*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.resolver.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.metadata.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.repository.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.target.facade.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.embedder.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.embedder.internal.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.model.*");
               // newRealm.importFrom(realm, "org.eclipse.tycho.p2.impl.publisher.*");
               // newRealm.importFrom(realm, "org.eclipse.equinox.p2.publisher.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.embedder.internal.*");
               // newRealm.importFrom(realm, "org.eclipse.sisu.equinox.embedder.internal.*");

            }
            return newRealm;
         };
      });
   }

   @Test
   public void test() throws Throwable
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

   private static void assertSuccess(MavenExecutionResult2 result) throws Throwable
   {
      for (Throwable throwable : result.getExceptions())
      {
         throw throwable;
      }
   }
}
