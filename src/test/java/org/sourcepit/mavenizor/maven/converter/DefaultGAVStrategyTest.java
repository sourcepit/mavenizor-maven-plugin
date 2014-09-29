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

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newBundleDescription;

import javax.inject.Inject;
import java.lang.IllegalArgumentException;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.junit.Test;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.mavenizor.AbstractMavenizorTest;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory.Request;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory.SnapshotRule;

public class DefaultGAVStrategyTest extends AbstractMavenizorTest
{
   @Inject
   private DefaultGAVStrategyFactory factory;

   @Test
   public void testDeriveGroupId()
   {
      final GAVStrategy converter = factory.newGAVStrategy(new GAVStrategyFactory.Request());
      try
      {
         converter.deriveGroupId((BundleDescription) null);
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      try
      {
         converter.deriveGroupId(newBundleDescription(null));
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      try
      {
         converter.deriveGroupId(newBundleDescription(""));
         fail();
      }
      catch (IllegalArgumentException e)
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
      assertThat(groupId, equalTo("org.eclipse"));

      bundle = newBundleDescription("org.eclipse.emf");
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.eclipse"));

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
   public void testDeriveGroupIdWithGroup3Prefix()
   {
      GAVStrategyFactory.Request request = new GAVStrategyFactory.Request();
      request.getGroup3Prefixes().add("org.eclipse");

      final GAVStrategy converter = factory.newGAVStrategy(request);

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
   public void testGroupIdMapping()
   {
      GAVStrategyFactory.Request request = new GAVStrategyFactory.Request();
      request.getGroupIdMappings().put("foo", "org.foo");
      request.getGroupIdMappings().put("!org.sourcepit.**", "srcpit_${bundle.groupId}");

      GAVStrategy converter = factory.newGAVStrategy(request);

      BundleDescription bundle = newBundleDescription("org.sourcepit.mavenizor.core");
      converter.deriveGroupId(bundle);
      String groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.sourcepit.mavenizor"));

      bundle = newBundleDescription("org.eclipse.emf.ecore");
      converter.deriveGroupId(bundle);
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("srcpit_org.eclipse"));

      bundle = newBundleDescription("foo");
      converter.deriveGroupId(bundle);
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("org.foo"));

      request.setGroupIdPrefix("mavenized");
      converter = factory.newGAVStrategy(request);

      bundle = newBundleDescription("foo");
      converter.deriveGroupId(bundle);
      groupId = converter.deriveGroupId(bundle);
      assertThat(groupId, equalTo("mavenized.org.foo"));
   }

   @Test
   public void testDeriveArtifactId()
   {
      final GAVStrategy converter = factory.newGAVStrategy(new GAVStrategyFactory.Request());
      try
      {
         converter.deriveArtifactId((BundleDescription) null);
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      try
      {
         converter.deriveArtifactId(newBundleDescription(null));
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      try
      {
         converter.deriveArtifactId(newBundleDescription(""));
         fail();
      }
      catch (IllegalArgumentException e)
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
      GAVStrategy converter = factory.newGAVStrategy(new GAVStrategyFactory.Request());
      try
      {
         converter.deriveMavenVersion(null);
         fail();
      }
      catch (IllegalArgumentException e)
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

      GAVStrategy converter = factory.newGAVStrategy(defaultRequest);

      BundleDescription bundle = newBundleDescription("foo", "1");
      String mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0"));

      final GAVStrategyFactory.Request customRequest = new GAVStrategyFactory.Request();
      customRequest.getAdditionalSnapshotRules().add(new SnapshotRule()
      {
         public boolean isSnapshotVersion(BundleDescription bundle, Version version)
         {
            return "foo".equals(bundle.getSymbolicName());
         }
      });

      converter = factory.newGAVStrategy(customRequest);

      bundle = newBundleDescription("foo", "1");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));
   }

   @Test
   public void testDisableDefaulSnapshotRules()
   {
      GAVStrategy converter = factory.newGAVStrategy(new GAVStrategyFactory.Request());

      BundleDescription bundle = newBundleDescription("foo", "1.0.0.qualifier");
      String mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));

      bundle = newBundleDescription("foo", "1.0.0.20120606-131029-1");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-SNAPSHOT"));

      final Request request = new Request();
      request.setUseDefaultSnapshotRules(false);

      converter = factory.newGAVStrategy(request);

      bundle = newBundleDescription("foo", "1.0.0.qualifier");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-qualifier"));

      bundle = newBundleDescription("foo", "1.0.0.20120606-131029-1");
      mavenVersion = converter.deriveMavenVersion(bundle);
      assertThat(mavenVersion, equalTo("1.0.0-20120606-131029-1"));
   }

   @Test
   public void testDeriveMavenVersionRange()
   {
      GAVStrategy converter = factory.newGAVStrategy(new GAVStrategyFactory.Request());
      try
      {
         converter.deriveMavenVersionRange(null, null);
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      try
      {
         BundleDescription bundle = newBundleDescription("foo", "1");
         converter.deriveMavenVersionRange(bundle, null);
         fail();
      }
      catch (IllegalArgumentException e)
      {
      }

      BundleDescription bundle = newBundleDescription("foo", "1");
      String mavenVersionRange = converter.deriveMavenVersionRange(bundle, VersionRange.parse("1.0.0"));
      assertThat(mavenVersionRange, equalTo("[1,)"));

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
}
