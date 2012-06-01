/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.ArtifactDescription;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;

public interface Mavenizor
{
   class Request
   {
      private State state;
      private BundleFilter inputFilter;
      private GAVStrategy gavStrategy;
      private PropertiesMap options = new LinkedPropertiesMap();

      public State getState()
      {
         return state;
      }

      public void setState(State state)
      {
         this.state = state;
      }

      public BundleFilter getInputFilter()
      {
         return inputFilter;
      }

      public void setInputFilter(BundleFilter inputFilter)
      {
         this.inputFilter = inputFilter;
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
   }

   class Result
   {
      private final List<BundleDescription> inputBundles = new ArrayList<BundleDescription>();
      private final List<BundleDescription> sourceBundles = new ArrayList<BundleDescription>();
      private final Map<BundleDescription, Collection<ArtifactDescription>> artifactDescriptors = new HashMap<BundleDescription, Collection<ArtifactDescription>>();

      public List<BundleDescription> getInputBundles()
      {
         return inputBundles;
      }

      public List<BundleDescription> getSourceBundles()
      {
         return sourceBundles;
      }

      public Map<BundleDescription, Collection<ArtifactDescription>> getArtifactDescriptors()
      {
         return artifactDescriptors;
      }
   }

   Result mavenize(Request request);
}