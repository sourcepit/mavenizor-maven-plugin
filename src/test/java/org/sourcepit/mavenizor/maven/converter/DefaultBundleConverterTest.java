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

package org.sourcepit.mavenizor.maven.converter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.sourcepit.common.utils.file.FileUtils.deleteFileOrDirectory;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addEmbeddedLibrary;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addMavenMetaData;
import static org.sourcepit.mavenizor.MavenizorTestHarness.getBundle;
import static org.sourcepit.mavenizor.MavenizorTestHarness.jar;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newBundle;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newManifest;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newState;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Before;
import org.junit.Test;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.maven.model.ArtifactKeyBuilder;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.ProjectKey;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.AbstractMavenizorTest;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
import org.sourcepit.mavenizor.maven.converter.BundleConverter.Request;
import org.sourcepit.mavenizor.maven.converter.BundleConverter.Result;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;

public class DefaultBundleConverterTest extends AbstractMavenizorTest {
   @Inject
   private DefaultBundleConverter converter;

   @Inject
   private GAVStrategyFactory gavStrategyFactory;

   private GAVStrategy gavStrategy;

   private File bundlesDir;

   private File workingDir;

   @Before
   @Override
   public void setUp() throws Exception {
      super.setUp();
      bundlesDir = new File(getWs().getRoot(), "bundles");
      workingDir = new File(getWs().getRoot(), "work");
      gavStrategy = gavStrategyFactory.newGAVStrategy(new GAVStrategyFactory.Request());
   }

   @Test
   public void testIgnoreBundle() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar");

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();
      options.put(bundle.toString() + "/embedded.jar", "mavenize");

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
      assertTrue(result.getConvertedArtifacts().get(1).isEmbeddedLibrary());

      options.put(bundle.toString(), "ignore");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(0));
   }

   private Request newRequest(BundleDescription bundle, PropertiesMap options) {
      Request request = new Request();
      request.setBundle(bundle);
      request.setTargetType(TargetType.JAVA);
      request.setGAVStrategy(gavStrategy);
      request.setOptions(options);
      request.setWorkingDirectory(workingDir);
      return request;
   }

   @Test
   public void testIgnoreLibrary() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar");

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();
      options.put(bundle.toString() + "/embedded.jar", "mavenize");

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
      assertTrue(result.getConvertedArtifacts().get(1).isEmbeddedLibrary());

      options.put(bundle.toString() + "/embedded.jar", "ignore");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(1));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
   }

   @Test
   public void testOmitMainBundle() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar");

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();
      options.put(bundle.toString() + "/embedded.jar", "mavenize");

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      ConvertedArtifact convertedArtifact = result.getConvertedArtifacts().get(0);
      assertFalse(convertedArtifact.isEmbeddedLibrary());
      assertTrue(result.getConvertedArtifacts().get(1).isEmbeddedLibrary());

      options.put(bundle.toString(), "omit");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(1));
      convertedArtifact = result.getConvertedArtifacts().get(0);
      assertThat(convertedArtifact.getMavenArtifact().getArtifactId(), IsEqual.equalTo("embedded"));
      assertTrue(convertedArtifact.isEmbeddedLibrary());
   }

   @Test
   public void testOmitMainBundleWithoutEmbeddedLibrary() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      newBundle(bundlesDir, mf);

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(1));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());

      options.put(bundle.toString(), "omit");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(0));
   }

   @Test
   public void testReplaceBundle() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar");
      newBundle(bundlesDir, mf);

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();
      options.put(bundle.toString() + "/embedded.jar", "mavenize");

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
      assertThat(result.getConvertedArtifacts().get(0).getDirective(), Is.is(ConvertionDirective.MAVENIZE));
      assertTrue(result.getConvertedArtifacts().get(1).isEmbeddedLibrary());
      assertThat(result.getConvertedArtifacts().get(1).getDirective(), Is.is(ConvertionDirective.MAVENIZE));

      options.put(bundle.toString(), "foo:bar:jar:2");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(1));
      ConvertedArtifact artifact = result.getConvertedArtifacts().get(0);
      assertThat(artifact.getDirective(), Is.is(ConvertionDirective.REPLACE));
      MavenArtifact mavenArtifact = artifact.getMavenArtifact();
      assertThat(mavenArtifact.getGroupId(), IsEqual.equalTo("foo"));
      assertThat(mavenArtifact.getArtifactId(), IsEqual.equalTo("bar"));
      assertThat(mavenArtifact.getVersion(), IsEqual.equalTo("2"));
   }

   @Test
   public void testReplaceLibrary() {
      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar");
      newBundle(bundlesDir, mf);

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();
      options.put(bundle.toString() + "/embedded.jar", "mavenize");

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
      assertThat(result.getConvertedArtifacts().get(0).getDirective(), Is.is(ConvertionDirective.MAVENIZE));
      assertTrue(result.getConvertedArtifacts().get(1).isEmbeddedLibrary());
      assertThat(result.getConvertedArtifacts().get(1).getDirective(), Is.is(ConvertionDirective.MAVENIZE));

      options.put(bundle.toString() + "/embedded.jar", "foo:bar:jar:2");

      result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      ConvertedArtifact artifact = result.getConvertedArtifacts().get(1);
      assertThat(artifact.getDirective(), Is.is(ConvertionDirective.REPLACE));
      MavenArtifact mavenArtifact = artifact.getMavenArtifact();
      assertThat(mavenArtifact.getGroupId(), IsEqual.equalTo("foo"));
      assertThat(mavenArtifact.getArtifactId(), IsEqual.equalTo("bar"));
      assertThat(mavenArtifact.getVersion(), IsEqual.equalTo("2"));
   }

   @Test
   public void testAutoDetectLibrary() {
      final ProjectKey expectedLib = new ArtifactKeyBuilder().setGroupId("hans")
         .setArtifactId("wurst")
         .setType("jar")
         .setVersion("3")
         .toArtifactKey()
         .getProjectKey();

      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar", expectedLib);

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertThat(result.getConvertedArtifacts().size(), Is.is(2));
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());

      ConvertedArtifact artifact = result.getConvertedArtifacts().get(1);
      assertTrue(artifact.isEmbeddedLibrary());
      assertThat(artifact.getDirective(), Is.is(ConvertionDirective.AUTO_DETECT));
      MavenArtifact mavenArtifact = artifact.getMavenArtifact();
      assertThat(mavenArtifact.getGroupId(), IsEqual.equalTo(expectedLib.getGroupId()));
      assertThat(mavenArtifact.getArtifactId(), IsEqual.equalTo(expectedLib.getArtifactId()));
      assertThat(mavenArtifact.getVersion(), IsEqual.equalTo(expectedLib.getVersion()));
   }

   @Test
   public void testAutoDetectLibraryAmbiguous() {
      final ProjectKey expectedLib = new ArtifactKeyBuilder().setGroupId("hans")
         .setArtifactId("wurst")
         .setType("jar")
         .setVersion("3")
         .toArtifactKey()
         .getProjectKey();

      final ProjectKey expectedLib2 = new ArtifactKeyBuilder().setGroupId("foo")
         .setArtifactId("bar")
         .setType("jar")
         .setVersion("3")
         .toArtifactKey()
         .getProjectKey();

      BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
      File bundleDir = newBundle(bundlesDir, mf);
      addEmbeddedLibrary(bundleDir, mf, ".");
      addEmbeddedLibrary(bundleDir, mf, "embedded.jar", expectedLib, expectedLib2);

      State state = newState(bundlesDir, mf);

      BundleDescription bundle = getBundle(state, "foo");

      PropertiesMap options = new LinkedPropertiesMap();

      Request request = newRequest(bundle, options);

      Result result = converter.toMavenArtifacts(request);
      assertFalse(result.getConvertedArtifacts().get(0).isEmbeddedLibrary());
      assertThat(result.getConvertedArtifacts().size(), Is.is(1));
      assertThat(result.getUnhandledEmbeddedLibraries().size(), Is.is(1));
   }

   @Test
   public void testAutoDetectBundle() throws IOException {
      for (int i = 0; i < 2; i++) {
         final boolean jar = i == 1;

         BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
         File bundleDir = newBundle(bundlesDir, mf);
         addEmbeddedLibrary(bundleDir, mf, "embedded.jar");

         final ProjectKey expectedGAV = new ArtifactKeyBuilder().setGroupId("hans")
            .setArtifactId("wurst")
            .setType("jar")
            .setType("jar")
            .setVersion("3")
            .toArtifactKey()
            .getProjectKey();

         addMavenMetaData(bundleDir, expectedGAV);

         if (jar) {
            jar(bundleDir);
            deleteFileOrDirectory(bundleDir);
         }

         State state = newState(bundlesDir, mf);

         BundleDescription bundle = getBundle(state, "foo");

         if (jar) {
            assertTrue(BundleAdapterFactory.DEFAULT.adapt(bundle, File.class).isFile());
         }
         else {
            assertTrue(BundleAdapterFactory.DEFAULT.adapt(bundle, File.class).isDirectory());
         }

         PropertiesMap options = new LinkedPropertiesMap();

         Request request = newRequest(bundle, options);

         Result result = converter.toMavenArtifacts(request);
         assertThat(result.getConvertedArtifacts().size(), Is.is(1));

         ConvertedArtifact artifact = result.getConvertedArtifacts().get(0);
         assertFalse(artifact.isEmbeddedLibrary());
         assertThat(artifact.getDirective(), Is.is(ConvertionDirective.AUTO_DETECT));

         MavenArtifact mavenArtifact = artifact.getMavenArtifact();
         assertThat(mavenArtifact.getGroupId(), IsEqual.equalTo(expectedGAV.getGroupId()));
         assertThat(mavenArtifact.getArtifactId(), IsEqual.equalTo(expectedGAV.getArtifactId()));
         assertThat(mavenArtifact.getVersion(), IsEqual.equalTo(expectedGAV.getVersion()));

         getWs().delete();
      }
   }
}
