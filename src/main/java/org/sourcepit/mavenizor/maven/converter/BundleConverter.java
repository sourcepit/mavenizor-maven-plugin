/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.Mavenizor.TargetType;

public interface BundleConverter
{
   class Result
   {
      private final List<MavenArtifact> mavenArtifacts = new ArrayList<MavenArtifact>();

      private final List<Path> unhandledEmbeddedLibraries = new ArrayList<Path>();

      private final List<Path> missingEmbeddedLibraries = new ArrayList<Path>();

      public List<MavenArtifact> getMavenArtifacts()
      {
         return mavenArtifacts;
      }

      public List<Path> getUnhandledEmbeddedLibraries()
      {
         return unhandledEmbeddedLibraries;
      }

      public List<Path> getMissingEmbeddedLibraries()
      {
         return missingEmbeddedLibraries;
      }
   }

   Result toMavenArtifacts(TargetType targetType, BundleDescription bundle, GAVStrategy converter, PropertiesMap options);
}
