/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.Mavenizor.TargetType;

public interface BundleConverter
{
   class Request
   {
      private BundleDescription bundle;
      private File workingDir;
      private TargetType targetType;
      private GAVStrategy gavStrategy;
      private PropertiesMap options;

      public TargetType getTargetType()
      {
         return targetType;
      }

      public void setTargetType(TargetType targetType)
      {
         this.targetType = targetType;
      }

      public BundleDescription getBundle()
      {
         return bundle;
      }

      public void setBundle(BundleDescription bundle)
      {
         this.bundle = bundle;
      }

      public GAVStrategy getGAVStrategy()
      {
         return gavStrategy;
      }

      public void setGAVStrategy(GAVStrategy gavStrategy)
      {
         this.gavStrategy = gavStrategy;
      }

      public PropertiesMap getOptions()
      {
         return options;
      }

      public void setOptions(PropertiesMap options)
      {
         this.options = options;
      }

      public File getWorkingDirectory()
      {
         return workingDir;
      }

      public void setWorkingDirectory(File workingDir)
      {
         this.workingDir = workingDir;
      }
   }

   class Result
   {
      private final BundleDescription bundle;
      
      private final ConvertionDirective convertionDirective;

      private final List<ConvertedArtifact> convertedArtifacts = new ArrayList<ConvertedArtifact>();

      private final List<Path> unhandledEmbeddedLibraries = new ArrayList<Path>();

      private final List<Path> missingEmbeddedLibraries = new ArrayList<Path>();

      public Result(BundleDescription bundle, ConvertionDirective directive)
      {
         this.bundle = bundle;
         this.convertionDirective = directive;
      }

      public BundleDescription getBundle()
      {
         return bundle;
      }
      
      public ConvertionDirective getConvertionDirective()
      {
         return convertionDirective;
      }

      public List<ConvertedArtifact> getConvertedArtifacts()
      {
         return convertedArtifacts;
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

   Result toMavenArtifacts(Request request);
}
