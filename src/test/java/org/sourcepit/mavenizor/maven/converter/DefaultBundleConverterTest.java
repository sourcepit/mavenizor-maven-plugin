/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
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
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;
import org.sourcepit.common.maven.model.VersionedIdentifiable;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.AbstractMavenizorTest;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
import org.sourcepit.mavenizor.maven.converter.BundleConverter.Request;
import org.sourcepit.mavenizor.maven.converter.BundleConverter.Result;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;

public class DefaultBundleConverterTest extends AbstractMavenizorTest
{
   @Inject
   private DefaultBundleConverter converter;

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
   public void testIgnoreBundle()
   {
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

   private Request newRequest(BundleDescription bundle, PropertiesMap options)
   {
      Request request = new Request();
      request.setBundle(bundle);
      request.setTargetType(TargetType.JAVA);
      request.setGAVStrategy(gavStrategy);
      request.setOptions(options);
      request.setWorkingDirectory(workingDir);
      return request;
   }

   @Test
   public void testIgnoreLibrary()
   {
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
   public void testOmitMainBundle()
   {
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
   public void testOmitMainBundleWithoutEmbeddedLibrary()
   {
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
   public void testReplaceBundle()
   {
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
   public void testReplaceLibrary()
   {
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
   public void testAutoDetectLibrary()
   {
      VersionedIdentifiable expectedLib = MavenModelFactory.eINSTANCE.createMavenArtifact();
      expectedLib.setGroupId("hans");
      expectedLib.setArtifactId("wurst");
      expectedLib.setVersion("3");

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
   public void testAutoDetectBundle() throws IOException
   {
      for (int i = 0; i < 2; i++)
      {
         final boolean jar = i == 1;

         BundleManifest mf = newManifest("foo", "1.0.0.qualifier");
         File bundleDir = newBundle(bundlesDir, mf);
         addEmbeddedLibrary(bundleDir, mf, "embedded.jar");

         VersionedIdentifiable expectedGAV = MavenModelFactory.eINSTANCE.createMavenArtifact();
         expectedGAV.setGroupId("hans");
         expectedGAV.setArtifactId("wurst");
         expectedGAV.setVersion("3");

         addMavenMetaData(bundleDir, expectedGAV);

         if (jar)
         {
            jar(bundleDir);
            org.apache.commons.io.FileUtils.forceDelete(bundleDir);
         }

         State state = newState(bundlesDir, mf);

         BundleDescription bundle = getBundle(state, "foo");

         if (jar)
         {
            assertTrue(BundleAdapterFactory.DEFAULT.adapt(bundle, File.class).isFile());
         }
         else
         {
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
