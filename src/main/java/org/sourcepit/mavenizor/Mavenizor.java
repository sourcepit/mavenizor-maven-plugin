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
import org.sourcepit.mavenizor.maven.ArtifactDescriptor;
import org.sourcepit.mavenizor.maven.ArtifactDescriptorsStrategy;
import org.sourcepit.mavenizor.maven.converter.Converter;

public interface Mavenizor
{
   class Request
   {
      private State state;
      private BundleFilter inputFilter;
      private Converter converter;
      private ArtifactDescriptorsStrategy artifactDescriptorsStrategy;

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

      public Converter getConverter()
      {
         return converter;
      }

      public void setConverter(Converter converter)
      {
         this.converter = converter;
      }

      public ArtifactDescriptorsStrategy getArtifactDescriptorsStrategy()
      {
         return artifactDescriptorsStrategy;
      }

      public void setArtifactDescriptorsStrategy(ArtifactDescriptorsStrategy deploymentDescriptorsStrategy)
      {
         this.artifactDescriptorsStrategy = deploymentDescriptorsStrategy;
      }
   }

   class Result
   {
      private final List<BundleDescription> inputBundles = new ArrayList<BundleDescription>();
      private final Map<BundleDescription, Collection<ArtifactDescriptor>> artifactDescriptors = new HashMap<BundleDescription, Collection<ArtifactDescriptor>>();

      public List<BundleDescription> getInputBundles()
      {
         return inputBundles;
      }

      public Map<BundleDescription, Collection<ArtifactDescriptor>> getArtifactDescriptors()
      {
         return artifactDescriptors;
      }
   }

   Result mavenize(Request request);
}