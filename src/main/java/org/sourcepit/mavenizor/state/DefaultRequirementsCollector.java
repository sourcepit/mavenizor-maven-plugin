/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.state;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Named;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.osgi.framework.Constants;
import org.sourcepit.common.manifest.osgi.VersionRange;

@Named
public class DefaultRequirementsCollector implements RequirementsCollector
{
   public Collection<Requirement> collectRequirements(BundleDescription bundle)
   {
      final Map<BundleDescription, Requirement> requirements = new LinkedHashMap<BundleDescription, Requirement>();
      collectRequirementsByBundleRequirements(bundle, requirements);
      collectRequirementsByPackageImports(bundle, requirements);
      requirements.remove(bundle); // remove self reference
      return requirements.values();
   }

   private static void collectRequirementsByBundleRequirements(BundleDescription bundle,
      final Map<BundleDescription, Requirement> requirements)
   {
      final Map<String, VersionRange> importVersioRange = new HashMap<String, VersionRange>();
      final Map<String, Boolean> importIsOptional = new HashMap<String, Boolean>();
      for (BundleSpecification spec : bundle.getRequiredBundles())
      {
         final String packageName = spec.getName();
         final VersionRange versionRange = VersionRange.parse(spec.getVersionRange().toString());
         importVersioRange.put(packageName, versionRange);

         final Boolean optional = Boolean.valueOf(spec.isOptional());
         importIsOptional.put(packageName, optional);
      }

      for (BundleDescription exporter : bundle.getResolvedRequires())
      {
         putRequitement(bundle, requirements, importVersioRange, importIsOptional, exporter, exporter.getName());
      }
   }

   private static void collectRequirementsByPackageImports(BundleDescription bundle,
      final Map<BundleDescription, Requirement> requirements)
   {
      final Map<String, VersionRange> importVersioRange = new HashMap<String, VersionRange>();
      final Map<String, Boolean> importIsOptional = new HashMap<String, Boolean>();
      for (ImportPackageSpecification spec : bundle.getImportPackages())
      {
         final String packageName = spec.getName();
         final VersionRange versionRange = VersionRange.parse(spec.getVersionRange().toString());
         importVersioRange.put(packageName, versionRange);

         final Boolean optional = Boolean.valueOf(Constants.RESOLUTION_OPTIONAL.equals(spec
            .getDirective(Constants.RESOLUTION_DIRECTIVE)));
         importIsOptional.put(packageName, optional);
      }

      for (ExportPackageDescription resolvedImport : bundle.getResolvedImports())
      {
         final BundleDescription exporter = resolvedImport.getExporter();
         final String packageName = resolvedImport.getName();
         putRequitement(bundle, requirements, importVersioRange, importIsOptional, exporter, packageName);
      }
   }

   private static void putRequitement(BundleDescription bundle, final Map<BundleDescription, Requirement> requirements,
      final Map<String, VersionRange> importVersioRange, final Map<String, Boolean> importIsOptional,
      final BundleDescription exporter, final String packageName)
   {
      Requirement requirement = requirements.get(exporter);
      if (requirement == null)
      {
         requirement = new Requirement();
         requirement.setFrom(bundle);
         requirement.setTo(exporter);
         requirement.setVersionRange(VersionRange.INFINITE_RANGE);

         final Boolean optional = importIsOptional.get(packageName);
         if (optional != null)
         {
            requirement.setOptional(optional.booleanValue());
         }
      }

      final Boolean optional = importIsOptional.get(packageName);
      if (optional != null)
      {
         requirement.setOptional(requirement.isOptional() ? optional.booleanValue() : false);
      }

      final VersionRange versionRange = importVersioRange.get(packageName);
      if (versionRange != null)
      {
         requirement.setVersionRange(VersionRange.intersect(requirement.getVersionRange(), versionRange));
      }

      requirements.put(exporter, requirement);
   }
}
