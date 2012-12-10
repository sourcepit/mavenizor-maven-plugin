/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.sourcepit.mavenizor.maven.converter.ConvertedArtifact;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;

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
      private File workingDir;
      private TargetType targetType;
      private State state;
      private BundleFilter inputFilter;
      private GAVStrategy gavStrategy;
      private PropertiesMap options = new LinkedPropertiesMap();
      private SourceJarResolver sourceJarResolver;

      public File getWorkingDirectory()
      {
         return workingDir;
      }

      public void setWorkingDirectory(File workingDir)
      {
         this.workingDir = workingDir;
      }

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
      
      public void setSourceJarResolver(SourceJarResolver sourceJarResolver)
      {
         this.sourceJarResolver = sourceJarResolver;
      }
      
      public SourceJarResolver getSourceJarResolver()
      {
         return sourceJarResolver;
      }
   }

   class Result
   {
      private final List<BundleDescription> inputBundles = new ArrayList<BundleDescription>();
      private final List<BundleDescription> sourceBundles = new ArrayList<BundleDescription>();
      private final Map<BundleDescription, BundleConverter.Result> bundleToconverterResultMap = new LinkedHashMap<BundleDescription, BundleConverter.Result>();
      private final Map<String, ArtifactBundle> gavToArtifactBundleMap = new LinkedHashMap<String, ArtifactBundle>();

      public List<BundleDescription> getInputBundles()
      {
         return inputBundles;
      }

      public List<BundleDescription> getSourceBundles()
      {
         return sourceBundles;
      }

      public BundleConverter.Result getConverterResult(BundleDescription bundle)
      {
         return bundleToconverterResultMap.get(bundle);
      }

      public List<BundleConverter.Result> getConverterResults()
      {
         return new ArrayList<BundleConverter.Result>(bundleToconverterResultMap.values());
      }

      public List<ArtifactBundle> getArtifactBundles()
      {
         return new ArrayList<ArtifactBundle>(gavToArtifactBundleMap.values());
      }

      public List<ConvertedArtifact> getConvertedArtifacts(BundleDescription bundle)
      {
         final List<ConvertedArtifact> convertedArtifacts = new ArrayList<ConvertedArtifact>();
         final BundleConverter.Result result = bundleToconverterResultMap.get(bundle);
         if (result != null)
         {
            convertedArtifacts.addAll(result.getConvertedArtifacts());
         }
         return convertedArtifacts;
      }

      public List<ArtifactBundle> getArtifactBundles(BundleDescription bundle)
      {
         final List<ArtifactBundle> artifactBundles = new ArrayList<ArtifactBundle>();
         final Collection<ConvertedArtifact> artifacts = getConvertedArtifacts(bundle);
         if (artifacts != null && !artifacts.isEmpty())
         {
            for (ConvertedArtifact artifact : artifacts)
            {
               final ArtifactBundle artifactBundle = getArtifactBundle(artifact);
               if (artifactBundle == null || artifactBundles.contains(artifactBundle))
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
         for (Entry<BundleDescription, BundleConverter.Result> entry : bundleToconverterResultMap.entrySet())
         {
            final BundleDescription bundle = entry.getKey();
            final BundleConverter.Result converterResult = entry.getValue();
            for (ConvertedArtifact artifact : converterResult.getConvertedArtifacts())
            {
               if (artifactBundle.equals(getArtifactBundle(artifact)))
               {
                  bundles.add(bundle);
                  break;
               }
            }
         }
         return bundles;
      }

      public ArtifactBundle getArtifactBundle(ConvertedArtifact artifact)
      {
         return getArtifactBundle(artifact, false);
      }

      private ArtifactBundle getArtifactBundle(ConvertedArtifact artifact, boolean createOnDemand)
      {
         final String gav = createGAV(artifact);
         ArtifactBundle artifactBundle = gavToArtifactBundleMap.get(gav);
         if (artifactBundle == null && createOnDemand)
         {
            artifactBundle = new ArtifactBundle();
            gavToArtifactBundleMap.put(gav, artifactBundle);
         }
         return artifactBundle;
      }

      public static void addConverterResult(Result result, BundleConverter.Result converterResult)
      {
         result.bundleToconverterResultMap.put(converterResult.getBundle(), converterResult);
         for (ConvertedArtifact convertedArtifact : converterResult.getConvertedArtifacts())
         {
            result.getArtifactBundle(convertedArtifact, true).getArtifacts().add(convertedArtifact);
         }
      }

      private String createGAV(ConvertedArtifact artifact)
      {
         return createGAV(artifact.getMavenArtifact());
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
   }

   Result mavenize(Request request);
}