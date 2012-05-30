/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.junit.Test;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.guplex.test.GuplexTest;
import org.sourcepit.mavenizor.maven.converter.Converter;
import org.sourcepit.mavenizor.maven.converter.ConverterFactory;
import org.sourcepit.mavenizor.maven.converter.DefaultConverterFactory;
import org.sourcepit.mavenizor.maven.converter.ConverterFactory.Request;
import org.sourcepit.mavenizor.maven.converter.ConverterFactory.SnapshotRule;

public class DefaultConverterTest extends GuplexTest
{
   @Inject
   private DefaultConverterFactory factory;

   @Test
   public void testDeriveGroupId()
   {
      final Converter converter = factory.newConverter(new ConverterFactory.Request());
      try
      {
         converter.deriveGroupId((BundleDescription) null);
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      try
      {
         converter.deriveGroupId(newBundleDescription(null));
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      try
      {
         converter.deriveGroupId(newBundleDescription(""));
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      BundleDescription bundle = newBundleDescription("org.sourcepit.mavenizor.core");
      String groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.sourcepit.mavenizor"));

      bundle = newBundleDescription("org.sourcepit.mavenizor.core");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.sourcepit.mavenizor"));

      bundle = newBundleDescription("org.eclipse.emf.core");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.eclipse.emf"));

      bundle = newBundleDescription("org.eclipse.emf");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.eclipse.emf"));

      bundle = newBundleDescription("foo.bar.core.bla");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("foo.bar"));

      bundle = newBundleDescription("foo.bar");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("foo.bar"));

      bundle = newBundleDescription("foo");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("foo"));
   }

   @Test
   public void testDeriveArtifactId()
   {
      final Converter converter = factory.newConverter(new ConverterFactory.Request());
      try
      {
         converter.deriveArtifactId((BundleDescription) null);
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      try
      {
         converter.deriveArtifactId(newBundleDescription(null));
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      try
      {
         converter.deriveArtifactId(newBundleDescription(""));
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      BundleDescription bundle = newBundleDescription("org.sourcepit.mavenizor.core");
      String artifactId = converter.deriveArtifactId(bundle);
      assertThat(artifactId, equalTo("org.sourcepit.mavenizor.core"));

      bundle = newBundleDescription("org.eclipse.emf.core");
      artifactId = converter.deriveArtifactId(bundle);
      assertThat(artifactId, equalTo("org.eclipse.emf.core"));

      bundle = newBundleDescription("foo.bar");
      artifactId = converter.deriveArtifactId(bundle);
      assertThat(artifactId, equalTo("foo.bar"));

      bundle = newBundleDescription("foo");
      artifactId = converter.deriveArtifactId(bundle);
      assertThat(artifactId, equalTo("foo"));
   }

   @Test
   public void testDeriveMavenVersion()
   {
      Converter converter = factory.newConverter(new ConverterFactory.Request());
      try
      {
         converter.deriveMavenVersion(null);
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      BundleDescription bundle = newBundleDescription("foo", "1");
      String mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0"));

      bundle = newBundleDescription("foo", "1.0.0");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0"));

      bundle = newBundleDescription("foo", "1.0.0.alpha-1");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-alpha-1"));

      bundle = newBundleDescription("foo", "1.0.0.qualifier");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));
   }

   @Test
   public void testDeriveCustomSnapshotVersion()
   {
      final Request defaultRequest = new Request();

      Converter converter = factory.newConverter(defaultRequest);

      BundleDescription bundle = newBundleDescription("foo", "1");
      String mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0"));

      final ConverterFactory.Request customRequest = new ConverterFactory.Request();
      customRequest.getAdditionalSnapshotRules().add(new SnapshotRule()
      {
         public boolean isSnapshotVersion(BundleDescription bundle, Version version)
         {
            return "foo".equals(bundle.getSymbolicName());
         }
      });

      converter = factory.newConverter(customRequest);

      bundle = newBundleDescription("foo", "1");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));
   }

   @Test
   public void testDisableDefaulSnapshotRules()
   {
      Converter converter = factory.newConverter(new ConverterFactory.Request());

      BundleDescription bundle = newBundleDescription("foo", "1.0.0.qualifier");
      String mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));

      final Request request = new Request();
      request.setUseDefaultSnapshotRules(false);

      converter = factory.newConverter(request);

      bundle = newBundleDescription("foo", "1.0.0.qualifier");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-qualifier"));
   }

   @Test
   public void testDeriveMavenVersionRange()
   {
      Converter converter = factory.newConverter(new ConverterFactory.Request());
      try
      {
         converter.deriveMavenVersionRange(null, null);
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      try
      {
         BundleDescription bundle = newBundleDescription("foo", "1");
         converter.deriveMavenVersionRange(bundle, null);
         fail();
      }
      catch (ConstraintViolationException e)
      {
      }

      BundleDescription bundle = newBundleDescription("foo", "1");
      String mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("1.0.0"));
      assertThat(mavenVersionRange, equalTo("1.0.0"));

      bundle = newBundleDescription("foo", "1");
      mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("1.0.0.qualifier"));
      assertThat(mavenVersionRange, equalTo("1.0.0-SNAPSHOT"));

      bundle = newBundleDescription("foo", "1.0.0");
      mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("[0.0.0,1.0.0]"));
      assertThat(mavenVersionRange, equalTo("[0,1]"));

      bundle = newBundleDescription("foo", "1.0.0");
      mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("[0.0.0.qualifier,1.0.0.foo]"));
      assertThat(mavenVersionRange, equalTo("[0,1]"));
      
      bundle = newBundleDescription("foo", "1.0.0");
      mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("[0.0.0,1.1.0)"));
      assertThat(mavenVersionRange, equalTo("[0,1.1)"));
      
      bundle = newBundleDescription("foo", "1.0.0");
      mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("[0.0.0,1.0.1)"));
      assertThat(mavenVersionRange, equalTo("[0,1.0.1)"));
   }

   private static BundleDescription newBundleDescription(String symbolicName)
   {
      return newBundleDescription(symbolicName, "1.0.0.qualifier");
   }

   private static BundleDescription newBundleDescription(String symbolicName, String version)
   {
      final BundleDescription bundle = mock(BundleDescription.class);
      when(bundle.getSymbolicName()).thenReturn(symbolicName);
      if (version != null)
      {
         when(bundle.getVersion()).thenReturn(new org.osgi.framework.Version(version));
      }
      return bundle;
   }

}
