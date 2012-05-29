/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.State;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.Version;
import org.sourcepit.common.manifest.osgi.VersionRange;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.maven.model.MavenModelFactory;

@Named
public class Mavenizor
{
   @Inject
   private Logger log;

   @Inject
   private RequirementsCollector requirementsCollector;

   @Inject
   private Converter converter;

   public void mavenize(State state)
   {
      final BundleFilter inputFilter = BundleFilter.ACCEPT_ALL;

      final Map<BundleDescription, Model> processed = new HashMap<BundleDescription, Model>();
      for (BundleDescription bundle : state.getBundles())
      {
         if (inputFilter.accept(bundle))
         {
            log.info(bundle.toString());
            mavenize(bundle, processed);
         }
      }
   }

   private void mavenize(BundleDescription bundle, Map<BundleDescription, Model> processed)
   {
      final List<Dependency> dependencies = new ArrayList<Dependency>();

      processDependenciesRecursive(bundle, dependencies, processed);

      final Model model = new Model();
      model.setGroupId(converter.deriveGroupId(bundle));
      model.setArtifactId(converter.deriveArtifactId(bundle));
      model.setVersion(converter.deriveVersion(bundle));
      model.setDependencies(dependencies);

      processed.put(bundle, model);
   }

   private void processDependenciesRecursive(BundleDescription bundle, List<Dependency> dependencies,
      Map<BundleDescription, Model> processed)
   {
      final Collection<Requirement> requirements = requirementsCollector.collectRequirements(bundle);
      for (Requirement requirement : requirements)
      {
         final BundleDescription requiredBundle = requirement.getTo();

         Model model = processed.get(requiredBundle);
         if (model == null)
         {
            mavenize(requiredBundle, processed);
            model = processed.get(requiredBundle);
         }

         final Dependency dependency = new Dependency();
         dependency.setGroupId(model.getGroupId());
         dependency.setArtifactId(model.getArtifactId());
         dependency.setOptional(requirement.isOptional());

         final Version requiredVersion = Version.parse(requiredBundle.getVersion().toString());
         VersionRange versionRange = requirement.getVersionRange();
         if (versionRange == null || VersionRange.INFINITE_RANGE.equals(versionRange)
            || !versionRange.includes(requiredVersion))
         {
            versionRange = VersionRange.parse(requiredVersion.toString());
         }

         dependency.setVersion(converter.toMavenVersionRange(versionRange));

         dependencies.add(dependency);
      }
   }


   private static interface BundleFilter
   {
      BundleFilter ACCEPT_ALL = new BundleFilter()
      {
         public boolean accept(BundleDescription bundle)
         {
            return true;
         }
      };

      boolean accept(BundleDescription bundle);
   }
}
