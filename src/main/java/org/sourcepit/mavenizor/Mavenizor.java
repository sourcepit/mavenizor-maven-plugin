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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.converter.BundleConverter;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.modeling.common.Annotation;

public interface Mavenizor
{
   public enum TargetType
   {
      OSGI, JAVA;

      private final String literal;

      private TargetType()
      {
         this.literal = name().toLowerCase();
      }

      public final String literal()
      {
         return literal;
      }

      public static TargetType valueOfLiteral(String literal)
      {
         for (TargetType mode : values())
         {
            if (mode.literal().equals(literal))
            {
               return mode;
            }
         }
         return null;
      }
   }

   class Request
   {
      private TargetType targetType;
      private State state;
      private BundleFilter inputFilter;
      private GAVStrategy gavStrategy;
      private PropertiesMap options = new LinkedPropertiesMap();

      public TargetType getTargetType()
      {
         return targetType;
      }

      public void setTargetType(TargetType targetType)
      {
         this.targetType = targetType;
      }

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
      private final Map<BundleDescription, Collection<MavenArtifact>> artifactDescriptors = new HashMap<BundleDescription, Collection<MavenArtifact>>();
      private final Map<BundleDescription, BundleConverter.Result> converterResults = new LinkedHashMap<BundleDescription, BundleConverter.Result>();
      private final Map<String, ArtifactBundle> mavenArtifactBundles = new HashMap<String, ArtifactBundle>();

      public List<BundleDescription> getInputBundles()
      {
         return inputBundles;
      }

      public List<BundleDescription> getSourceBundles()
      {
         return sourceBundles;
      }

      public Map<BundleDescription, Collection<MavenArtifact>> getBundleToMavenArtifactsMap()
      {
         return artifactDescriptors;
      }

      public Map<String, ArtifactBundle> getGAVToArtifactBundleMap()
      {
         return mavenArtifactBundles;
      }

      public Map<BundleDescription, BundleConverter.Result> getBundleToBundleConverterResultMap()
      {
         return converterResults;
      }

      public Set<ArtifactBundle> getArtifactBundles(BundleDescription bundle)
      {
         final Set<ArtifactBundle> artifactBundles = new LinkedHashSet<ArtifactBundle>();
         final Collection<MavenArtifact> artifacts = getBundleToMavenArtifactsMap().get(bundle);
         if (artifacts != null && !artifacts.isEmpty())
         {
            for (MavenArtifact artifact : artifacts)
            {
               final ArtifactBundle artifactBundle = getArtifactBundle(artifact, false);
               if (artifactBundle == null)
               {
                  throw new IllegalStateException();
               }
               artifactBundles.add(artifactBundle);
            }
         }
         return artifactBundles;
      }

      public Set<BundleDescription> getBundles(ArtifactBundle artifactBundle)
      {
         final Set<BundleDescription> bundles = new HashSet<BundleDescription>();
         for (Entry<BundleDescription, Collection<MavenArtifact>> entry : getBundleToMavenArtifactsMap().entrySet())
         {
            final BundleDescription bundle = entry.getKey();
            for (MavenArtifact mavenArtifact : entry.getValue())
            {
               if (artifactBundle.equals(getArtifactBundle(mavenArtifact, false)))
               {
                  bundles.add(bundle);
                  break;
               }
            }
         }
         return bundles;
      }

      public ArtifactBundle getArtifactBundle(MavenArtifact artifact, boolean createOnDemand)
      {
         final String gav = createGAV(artifact);
         ArtifactBundle artifactBundle = getGAVToArtifactBundleMap().get(gav);
         if (artifactBundle == null && createOnDemand)
         {
            artifactBundle = new ArtifactBundle();
            getGAVToArtifactBundleMap().put(gav, artifactBundle);
         }
         return artifactBundle;
      }

      private String createGAV(MavenArtifact artifact)
      {
         final StringBuilder sb = new StringBuilder();
         sb.append(artifact.getGroupId());
         sb.append(':');
         sb.append(artifact.getArtifactId());
         sb.append(':');
         sb.append(artifact.getVersion());
         return sb.toString();
      }

      public List<MavenArtifact> getEmbeddedArtifacts(BundleDescription bundle)
      {
         final List<MavenArtifact> embeddedArtifacts = new ArrayList<MavenArtifact>();
         for (MavenArtifact artifact : getBundleToMavenArtifactsMap().get(bundle))
         {
            if (isEmbeddedArtifact(artifact))
            {
               embeddedArtifacts.add(artifact);
            }
         }
         return embeddedArtifacts;
      }

      public static boolean isMavenized(MavenArtifact artifact)
      {
         final Annotation annotation = artifact.getAnnotation("mavenizor");
         return annotation != null && annotation.getData("mavenized", false);
      }

      public static boolean isEmbeddedArtifact(MavenArtifact artifact)
      {
         final Annotation annotation = artifact.getAnnotation("mavenizor");
         return annotation != null && annotation.getData("embeddedArtifact", false);
      }


      public static void markAsMavenized(final MavenArtifact artifact)
      {
         artifact.getAnnotation("mavenizor", true).setData("mavenized", true);
      }

      public static void markAsEmbeddedArtifact(final MavenArtifact artifact)
      {
         artifact.getAnnotation("mavenizor", true).setData("embeddedArtifact", true);
      }
   }

   Result mavenize(Request request);
}