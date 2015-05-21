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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.Mavenizor.TargetType;

public interface BundleConverter {
   class Request {
      private BundleDescription bundle;
      private File workingDir;
      private TargetType targetType;
      private GAVStrategy gavStrategy;
      private PropertiesMap options;

      public TargetType getTargetType() {
         return targetType;
      }

      public void setTargetType(TargetType targetType) {
         this.targetType = targetType;
      }

      public BundleDescription getBundle() {
         return bundle;
      }

      public void setBundle(BundleDescription bundle) {
         this.bundle = bundle;
      }

      public GAVStrategy getGAVStrategy() {
         return gavStrategy;
      }

      public void setGAVStrategy(GAVStrategy gavStrategy) {
         this.gavStrategy = gavStrategy;
      }

      public PropertiesMap getOptions() {
         return options;
      }

      public void setOptions(PropertiesMap options) {
         this.options = options;
      }

      public File getWorkingDirectory() {
         return workingDir;
      }

      public void setWorkingDirectory(File workingDir) {
         this.workingDir = workingDir;
      }
   }

   class Result {
      private final BundleDescription bundle;

      private final ConvertionDirective convertionDirective;

      private final List<ConvertedArtifact> convertedArtifacts = new ArrayList<ConvertedArtifact>();

      private final List<Path> unhandledEmbeddedLibraries = new ArrayList<Path>();

      private final List<Path> missingEmbeddedLibraries = new ArrayList<Path>();

      public Result(BundleDescription bundle, ConvertionDirective directive) {
         this.bundle = bundle;
         this.convertionDirective = directive;
      }

      public BundleDescription getBundle() {
         return bundle;
      }

      public ConvertionDirective getConvertionDirective() {
         return convertionDirective;
      }

      public List<ConvertedArtifact> getConvertedArtifacts() {
         return convertedArtifacts;
      }

      public List<Path> getUnhandledEmbeddedLibraries() {
         return unhandledEmbeddedLibraries;
      }

      public List<Path> getMissingEmbeddedLibraries() {
         return missingEmbeddedLibraries;
      }
   }

   Result toMavenArtifacts(Request request);
}
