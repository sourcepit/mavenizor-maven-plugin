/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.state;

import static org.junit.Assert.assertThat;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addBundleRequirement;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addPackageExport;
import static org.sourcepit.mavenizor.MavenizorTestHarness.addPackageImport;
import static org.sourcepit.mavenizor.MavenizorTestHarness.getBundle;
import static org.sourcepit.mavenizor.MavenizorTestHarness.newManifest;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.junit.Test;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.mavenizor.AbstractMavenizorTest;
import org.sourcepit.mavenizor.MavenizorTestHarness;

public class DefaultRequirementsCollectorTest extends AbstractMavenizorTest
{
   @Inject
   private DefaultRequirementsCollector collector;

   @Test
   public void testSelfReference()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      addPackageImport(manifestA, "package.a", "[1,2)");
      save(manifestA);

      final State state = createState(manifestA);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");

      Collection<Requirement> requirements = collector.collectRequirements(bundleA);
      assertThat(requirements.size(), Is.is(0));
   }

   @Test
   public void testPackageImport()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testPackageImportIsOptional()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }

   @Test
   public void testPackageImportWithRecommendedVersion()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "4");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", "3");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("3.0.0")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testPackageImportWithRange()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "4");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[2,6)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[2,6)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testPackageImportWithIntersectedRange()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "4");
      addPackageExport(manifestA, "package.b", "4");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[2,6)");
      addPackageImport(manifestB, "package.b", "[1,5)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[2,5)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testBundleRequirement()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addBundleRequirement(manifestB, "a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testBundleRequirementIsOptional()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }

   @Test
   public void testBundleRequirementWithRange()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixed()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      save(manifestB);

      final BundleManifest manifestC = newManifest("c", "2");
      addPackageImport(manifestC, "package.a", null);
      addBundleRequirement(manifestC, "b", null);
      save(manifestC);

      final State state = createState(manifestA, manifestB, manifestC);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");
      BundleDescription bundleC = getBundle(state, "c");

      Collection<Requirement> requirements = collector.collectRequirements(bundleC);
      assertThat(requirements.size(), Is.is(2));

      final Iterator<Requirement> it = requirements.iterator();
      Requirement requirement = it.next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleC));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));

      requirement = it.next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleC));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundle()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      addBundleRequirement(manifestB, "a", null);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleWithRange()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null);
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleWithRange2()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", "1");
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", "[1,1.2)");
      addBundleRequirement(manifestB, "a", "[1,2)");
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.parse("[1,1.2)")));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      addBundleRequirement(manifestB, "a", null, false);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional2()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, false);
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(false));
   }

   @Test
   public void testMixedToSameBundleIsOptional3()
   {
      final BundleManifest manifestA = newManifest("a", "1");
      addPackageExport(manifestA, "package.a", null);
      save(manifestA);

      final BundleManifest manifestB = newManifest("b", "2");
      addPackageImport(manifestB, "package.a", null, true);
      addBundleRequirement(manifestB, "a", null, true);
      save(manifestB);

      final State state = createState(manifestA, manifestB);
      state.resolve(false);

      BundleDescription bundleA = getBundle(state, "a");
      BundleDescription bundleB = getBundle(state, "b");

      Collection<Requirement> requirements = collector.collectRequirements(bundleB);
      assertThat(requirements.size(), Is.is(1));

      Requirement requirement = requirements.iterator().next();
      assertThat(requirement.getFrom(), IsEqual.equalTo(bundleB));
      assertThat(requirement.getTo(), IsEqual.equalTo(bundleA));
      assertThat(requirement.getVersionRange(), IsEqual.equalTo(VersionRange.INFINITE_RANGE));
      assertThat(requirement.isOptional(), IsEqual.equalTo(true));
   }


   private State createState(BundleManifest... manifests)
   {
      final File bundlesDir = getWs().getRoot();
      return MavenizorTestHarness.newState(bundlesDir, manifests);
   }

   private void save(BundleManifest manifest)
   {
      final File bundlesDir = getWs().getRoot();
      MavenizorTestHarness.newBundle(bundlesDir, manifest);
   }
}
