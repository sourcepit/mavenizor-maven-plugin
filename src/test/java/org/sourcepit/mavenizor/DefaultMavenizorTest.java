/*
 * Copyright 2014 Bernd Vogt and others.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sourcepit.mavenizor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addBundleRequirement;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addEmbeddedLibrary;
import static org.sourcepit.mavenizor.MavenizorTestHarness.getBundle;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newBundle;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newManifest;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newState;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Before;
import org.junit.Test;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.maven.model.ArtifactKeyBuilder;
import org.sourcepit.common.maven.model.ProjectKey;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
import org.sourcepit.mavenizor.maven.converter.BundleConverter;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory;

public class DefaultMavenizorTest extends AbstractMavenizorTest
{
   @Inject
   private DefaultMavenizor mavenizor;

   @Inject
   private GAVStrategyFactory gavStrategyFactory;

   private GAVStrategy gavStrategy;

   private File bundlesDir;

   private File workingDir;

   @Before
   @Override
   public void setUp() throws Exception
   {
      super.setUp();
      bundlesDir = new File(getWs().getRoot(), "bundles");
      workingDir = new File(getWs().getRoot(), "work");
      gavStrategy = gavStrategyFactory.newGAVStrategy(new GAVStrategyFactory.Request());
   }

   @Test
   public void testSingleBundle()
   {
      // expecting same behavior for all target types
      for (TargetType targetType : TargetType.values())
      {
         BundleManifest manifest = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
         newBundle(bundlesDir, manifest);

         State osgiState = newState(bundlesDir, manifest);

         Mavenizor.Request request = newRequest(osgiState, targetType);

         Mavenizor.Result result = mavenizor.mavenize(request);
         assertThat(result.getConverterResults().size(), Is.is(1));
         assertThat(result.getArtifactBundles().size(), Is.is(1));

         BundleDescription bundle = getBundle(osgiState, "org.sourcepit.testbundle");

         BundleConverter.Result converterResult = result.getConverterResult(bundle);
         assertNotNull(converterResult);

         List<ArtifactBundle> artifactBundles = result.getArtifactBundles(bundle);
         assertThat(artifactBundles.size(), Is.is(1));

         ArtifactBundle artifactBundle = artifactBundles.iterator().next();
         Model pom = artifactBundle.getPom();
         assertNotNull(pom);
         assertNotNull(pom.getGroupId());
         assertNotNull(pom.getArtifactId());
         assertNotNull(pom.getVersion());
         assertThat(pom.getDependencies().size(), Is.is(0));

         getWs().delete();
      }
   }

   @Test
   public void testBundleWithEmbeddedArtifact()
   {
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();

      BundleManifest manifest = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest);
      addEmbeddedLibrary(bundleDir, manifest, ".");
      addEmbeddedLibrary(bundleDir, manifest, "embedded.jar", libGav);

      State osgiState = newState(bundlesDir, manifest);

      BundleDescription bundle = getBundle(osgiState, "org.sourcepit.testbundle");

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(1));
      assertThat(result.getArtifactBundles().size(), Is.is(2));
      assertThat(result.getArtifactBundles(bundle).size(), Is.is(2));

      // first is bundle
      ArtifactBundle artifactBundle = result.getArtifactBundles().get(0);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertFalse(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(1));

      // second is embedded artifact
      artifactBundle = result.getArtifactBundles().get(1);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertTrue(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("wurst"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(0));
   }

   @Test
   public void testBundleWithEmbeddedArtifact_OmitBundle()
   {
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();

      BundleManifest manifest = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest);
      addEmbeddedLibrary(bundleDir, manifest, "embedded.jar", libGav);

      State osgiState = newState(bundlesDir, manifest);

      BundleDescription bundle = getBundle(osgiState, "org.sourcepit.testbundle");

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      request.getOptions().put(bundle.getSymbolicName(), "omit");

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(1));
      assertThat(result.getArtifactBundles().size(), Is.is(1));
      assertThat(result.getArtifactBundles(bundle).size(), Is.is(1));

      ArtifactBundle artifactBundle = result.getArtifactBundles().get(0);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertTrue(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("wurst"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(0));
   }

   @Test
   public void testBundleWithEmbeddedArtifact_AutoOmitBundleAndEmeddedIsMain()
   {
      BundleManifest manifest = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest);

      addEmbeddedLibrary(bundleDir, manifest, "z.jar", (ProjectKey[]) null);
      addEmbeddedLibrary(bundleDir, manifest, "a.jar", (ProjectKey[]) null);
      addEmbeddedLibrary(bundleDir, manifest, "lib/foo.jar", (ProjectKey[]) null);

      State osgiState = newState(bundlesDir, manifest);

      BundleDescription bundle = getBundle(osgiState, "org.sourcepit.testbundle");

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      request.getOptions().put(bundle.getSymbolicName() + "/z.jar", "mavenize");
      request.getOptions().put(bundle.getSymbolicName() + "/a.jar", "mavenize");
      request.getOptions().put(bundle.getSymbolicName() + "/lib/foo.jar", "mavenize");

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(1));
      assertThat(result.getArtifactBundles().size(), Is.is(3));
      assertThat(result.getArtifactBundles(bundle).size(), Is.is(3));

      ArtifactBundle artifactBundle = result.getArtifactBundles().get(0);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertTrue(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("z"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(2));

      assertThat(pom.getDependencies().get(0).getArtifactId(), IsEqual.equalTo("a"));
      assertThat(pom.getDependencies().get(1).getArtifactId(), IsEqual.equalTo("foo"));

      artifactBundle = result.getArtifactBundles().get(1);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertTrue(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("a"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(0));

      artifactBundle = result.getArtifactBundles().get(2);
      assertThat(artifactBundle.getArtifacts().size(), Is.is(1));
      assertTrue(artifactBundle.getArtifacts().get(0).isEmbeddedLibrary());

      pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("foo"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(0));
   }

   @Test
   public void testBundleWithEmbeddedArtifact_IgnoreBundle()
   {
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();

      BundleManifest manifest = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest);
      addEmbeddedLibrary(bundleDir, manifest, "embedded.jar", libGav);

      State osgiState = newState(bundlesDir, manifest);

      BundleDescription bundle = getBundle(osgiState, "org.sourcepit.testbundle");

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      request.getOptions().put(bundle.getSymbolicName(), "ignore");

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(1));
      assertThat(result.getArtifactBundles().size(), Is.is(0));
      assertThat(result.getArtifactBundles(bundle).size(), Is.is(0));
   }

   @Test
   public void testBundleWithDependencyToBundleWithEmbeddedArtifact() throws IOException
   {
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();

      BundleManifest manifest1 = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest1);
      addEmbeddedLibrary(bundleDir, manifest1, ".");
      addEmbeddedLibrary(bundleDir, manifest1, "embedded.jar", libGav);

      BundleManifest manifest2 = newManifest("org.sourcepit.testbundle2", "1.0.0.qualifier");
      newBundle(bundlesDir, manifest2);
      addBundleRequirement(manifest2, manifest1.getBundleSymbolicName().getSymbolicName(), "0.0.0");
      manifest2.eResource().save(null);

      State osgiState = newState(bundlesDir, manifest1, manifest2);

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(2));
      assertThat(result.getArtifactBundles().size(), Is.is(3));

      BundleDescription bundle = getBundle(osgiState, manifest2.getBundleSymbolicName().getSymbolicName());
      List<ArtifactBundle> artifactBundles = result.getArtifactBundles(bundle);
      assertThat(artifactBundles.size(), Is.is(1));

      // org.sourcepit.testbundle2
      ArtifactBundle artifactBundle = artifactBundles.iterator().next();
      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle2"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(1));

      Dependency dependency = pom.getDependencies().get(0);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("1.0.0-SNAPSHOT"));
   }

   @Test
   public void testBundleWithDependencyToBundleWithEmbeddedArtifact_OmitBundle() throws IOException
   {
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();

      BundleManifest manifest1 = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest1);
      addEmbeddedLibrary(bundleDir, manifest1, "embedded.jar", libGav);
      addEmbeddedLibrary(bundleDir, manifest1, "embedded2.jar");

      BundleManifest manifest2 = newManifest("org.sourcepit.testbundle2", "1.0.0.qualifier");
      newBundle(bundlesDir, manifest2);
      addBundleRequirement(manifest2, manifest1.getBundleSymbolicName().getSymbolicName(), "[1,2)");
      manifest2.eResource().save(null);

      State osgiState = newState(bundlesDir, manifest1, manifest2);

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      request.getOptions().put(manifest1.getBundleSymbolicName().getSymbolicName(), "omit");
      request.getOptions().put(manifest1.getBundleSymbolicName().getSymbolicName() + "/embedded2.jar", "mavenize");

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(2));
      assertThat(result.getArtifactBundles().size(), Is.is(3));

      BundleDescription bundle = getBundle(osgiState, manifest2.getBundleSymbolicName().getSymbolicName());
      List<ArtifactBundle> artifactBundles = result.getArtifactBundles(bundle);
      assertThat(artifactBundles.size(), Is.is(1));

      // org.sourcepit.testbundle2
      ArtifactBundle artifactBundle = artifactBundles.iterator().next();
      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle2"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(2));

      Dependency dependency = pom.getDependencies().get(0);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("wurst"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("3"));

      dependency = pom.getDependencies().get(1);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("embedded2"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("1.0.0-SNAPSHOT"));
   }

   @Test
   public void testBundleWithEmbeddedArtifactWithDependencyToBundle() throws IOException
   {
      BundleManifest manifest1 = newManifest("org.sourcepit.testbundle", "1.0.0.qualifier");
      newBundle(bundlesDir, manifest1);
      manifest1.eResource().save(null);

      BundleManifest manifest2 = newManifest("org.sourcepit.testbundle2", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, manifest2);
      addBundleRequirement(manifest2, manifest1.getBundleSymbolicName().getSymbolicName(), "[1,2)");
      final ProjectKey libGav = new ArtifactKeyBuilder().setGroupId("hans").setArtifactId("wurst").setType("jar")
         .setVersion("3").toArtifactKey().getProjectKey();
      addEmbeddedLibrary(bundleDir, manifest2, ".");
      addEmbeddedLibrary(bundleDir, manifest2, "embedded.jar", libGav);
      addEmbeddedLibrary(bundleDir, manifest2, "embedded2.jar");

      State osgiState = newState(bundlesDir, manifest1, manifest2);

      Mavenizor.Request request = newRequest(osgiState, TargetType.JAVA);
      request.getOptions().put(manifest2.getBundleSymbolicName().getSymbolicName() + "/embedded2.jar", "mavenize");

      Mavenizor.Result result = mavenizor.mavenize(request);
      assertThat(result.getConverterResults().size(), Is.is(2));
      assertThat(result.getArtifactBundles().size(), Is.is(4));

      BundleDescription bundle = getBundle(osgiState, manifest2.getBundleSymbolicName().getSymbolicName());
      List<ArtifactBundle> artifactBundles = result.getArtifactBundles(bundle);
      assertThat(artifactBundles.size(), Is.is(3));

      ArtifactBundle artifactBundle = artifactBundles.get(0);
      Model pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle2"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(3));

      Dependency dependency = pom.getDependencies().get(0);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("wurst"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("3"));

      dependency = pom.getDependencies().get(1);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("embedded2"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("1.0.0-SNAPSHOT"));

      dependency = pom.getDependencies().get(2);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("1.0.0-SNAPSHOT"));

      artifactBundle = artifactBundles.get(1);
      pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("wurst"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(0));

      artifactBundle = artifactBundles.get(2);
      pom = artifactBundle.getPom();
      assertNotNull(pom);
      assertNotNull(pom.getGroupId());
      assertThat(pom.getArtifactId(), IsEqual.equalTo("embedded2"));
      assertNotNull(pom.getVersion());
      assertThat(pom.getDependencies().size(), Is.is(1));

      dependency = pom.getDependencies().get(0);
      assertThat(dependency.getArtifactId(), IsEqual.equalTo("org.sourcepit.testbundle"));
      assertThat(dependency.getVersion(), IsEqual.equalTo("1.0.0-SNAPSHOT"));
   }


   private Mavenizor.Request newRequest(State osgiState, TargetType targetType)
   {
      final Mavenizor.Request request = new Mavenizor.Request();
      request.setState(osgiState);
      request.setGAVStrategy(gavStrategy);
      request.setInputFilter(BundleFilter.ACCEPT_ALL);
      request.setTargetType(targetType);
      request.setWorkingDirectory(workingDir);
      return request;
   }

}
