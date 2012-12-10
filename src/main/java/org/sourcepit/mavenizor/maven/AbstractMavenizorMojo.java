/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.core.ArtifactDependencyVisitor;
import org.eclipse.tycho.core.ArtifactDependencyWalker;
import org.eclipse.tycho.core.BundleProject;
import org.eclipse.tycho.core.PluginDescription;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TychoProject;
import org.eclipse.tycho.core.facade.TargetEnvironment;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.osgitools.EclipseFeatureProject;
import org.eclipse.tycho.core.utils.TychoProjectUtils;
import org.slf4j.Logger;
import org.sourcepit.common.manifest.osgi.BundleManifest;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.common.utils.lang.PipedException;
import org.sourcepit.common.utils.path.Path;
import org.sourcepit.common.utils.path.PathMatcher;
import org.sourcepit.common.utils.props.LinkedPropertiesMap;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.BundleFilter;
import org.sourcepit.mavenizor.Mavenizor;
import org.sourcepit.mavenizor.Mavenizor.Result;
import org.sourcepit.mavenizor.Mavenizor.TargetType;
import org.sourcepit.mavenizor.SourceJarResolver;
import org.sourcepit.mavenizor.maven.BundleResolver.Handler;
import org.sourcepit.mavenizor.maven.converter.BundleConverter;
import org.sourcepit.mavenizor.maven.converter.ConvertionDirective;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;
import org.sourcepit.mavenizor.maven.converter.GAVStrategyFactory;
import org.sourcepit.mavenizor.state.BundleAdapterFactory;
import org.sourcepit.mavenizor.state.OsgiStateBuilder;

import com.google.inject.name.Named;

public abstract class AbstractMavenizorMojo extends AbstractGuplexedMojo
{
   private final class MultiProperty extends HashSet<String>
   {
      private static final long serialVersionUID = 1L;

      @Override
      public boolean equals(Object o)
      {
         return isEmpty() || contains(o);
      }
   }

   @Inject
   protected Logger logger;

   /** @parameter expression="${session}" */
   protected MavenSession session;

   /** @parameter expression="${project}" */
   protected MavenProject project;

   /** @parameter */
   private Properties options;

   /** @parameter expression="${workingDir}" default-value="${project.build.directory}/mavenizor" */
   protected File workingDir;

   /** @parameter default-value=false */
   private boolean trimQualifiers;

   /** @parameter */
   private String groupIdPrefix;

   /** @parameter */
   private Set<String> group3Prefixes;

   /** @parameter */
   private List<String> groupIdMappings;

   /** @parameter expression="${dryRun}" default-value=false */
   private boolean dryRun;

   /** @parameter */
   private List<String> inputBundles;

   /** @parameter */
   private List<String> libraryMappings;

   /** @parameter */
   private TargetType targetType;

   private Set<File> bundleLocationsInBuildScope;

   @Inject
   @Named("tycho-project")
   private BundleResolver bundleResolver;

   @Inject
   private Mavenizor mavenizor;

   @Inject
   private GAVStrategyFactory gavStrategyFactory;

   @Inject
   private Map<String, TychoProject> projectTypes;

   public void setTargetType(String targetType)
   {
      this.targetType = TargetType.valueOfLiteral(targetType);
   }

   @Override
   protected void doExecute() throws PipedException
   {
      Result result = (Result) project.getContextValue("mavenizor.result");
      if (result == null)
      {
         result = doMavenize();
         writePropertyTemplate(result);

         for (BundleConverter.Result converterResult : result.getConverterResults())
         {
            if (!converterResult.getUnhandledEmbeddedLibraries().isEmpty())
            {
               throw Exceptions.pipe(new MojoExecutionException(
                  "Unhandled embedded libraries detected. See build log for details."));
            }
         }

         project.setContextValue("mavenizor.result", result);
      }


      if (!dryRun)
      {
         processResult(result);
      }
   }

   private void writePropertyTemplate(Result result)
   {
      final PropertiesMap template = new LinkedPropertiesMap();

      for (BundleConverter.Result converterResult : result.getConverterResults())
      {
         final BundleDescription bundle = converterResult.getBundle();

         for (Path path : converterResult.getUnhandledEmbeddedLibraries())
         {
            final StringBuilder value = new StringBuilder();
            for (ConvertionDirective directive : ConvertionDirective.values())
            {
               switch (directive)
               {
                  case REPLACE :
                     value.append("<groupId>:<artifactId>:<type>[:<classifier>]:<version>");
                     break;
                  default :
                     value.append(directive.literal());
                     break;
               }
               value.append(" | ");
            }
            value.delete(value.length() - 3, value.length());

            final StringBuilder key = new StringBuilder();
            key.append(bundle.getSymbolicName());
            key.append("[_");
            key.append(bundle.getVersion());
            key.append("]/");
            key.append(path);

            template.put(key.toString(), value.toString());
         }

         for (Path path : converterResult.getMissingEmbeddedLibraries())
         {
            final StringBuilder key = new StringBuilder();
            key.append(bundle.getSymbolicName());
            key.append("[_");
            key.append(bundle.getVersion());
            key.append("]/");
            key.append(path);

            template.put(key.toString(), ConvertionDirective.IGNORE.literal());
         }
      }

      template.store(new File(workingDir, "lib.properties"));
   }

   private TargetType determineTargetType()
   {
      if (targetType == null)
      {
         throw new IllegalArgumentException("Target type not specified");
      }
      return targetType;
   }

   protected Result doMavenize()
   {
      final OsgiStateBuilder stateBuilder = new OsgiStateBuilder(TychoProjectUtils.class.getClassLoader());
      addPlatformProperties(session, stateBuilder);
      addBundles(stateBuilder);

      final State state = resolveState(stateBuilder);

      final Mavenizor.Request request = new Mavenizor.Request();
      populateRequest(request);
      request.setState(state);
      return mavenizor.mavenize(request);
   }

   private BundleFilter newInputFilter()
   {
      return new BundleFilter()
      {
         private final PathMatcher macher = newInputBundleSymbolicNameMatcher();

         public boolean accept(BundleDescription bundle)
         {
            if (isEclipseSourceBundle(bundle))
            {
               return true;
            }
            if (macher == null || macher.isMatch(bundle.getSymbolicName()))
            {
               final File location = BundleAdapterFactory.DEFAULT.adapt(bundle, File.class);
               return getBundleLocationsInBuildScope().contains(location);
            }
            return false;
         }

         private boolean isEclipseSourceBundle(BundleDescription bundle)
         {
            final BundleManifest manifest = BundleAdapterFactory.DEFAULT.adapt(bundle, BundleManifest.class);
            return manifest.getHeaderValue("Eclipse-SourceBundle") != null;
         }
      };
   }

   private PathMatcher newInputBundleSymbolicNameMatcher()
   {
      final PathMatcher macher;
      if (inputBundles != null && !inputBundles.isEmpty())
      {
         StringBuilder sb = new StringBuilder();
         for (String filterPattern : inputBundles)
         {
            sb.append(filterPattern);
            sb.append(',');
         }
         sb.deleteCharAt(sb.length() - 1);

         macher = PathMatcher.parse(sb.toString(), ".", ",");
      }
      else
      {
         macher = null;
      }
      return macher;
   }

   private Set<File> determineBundleLocationsInBuildScope()
   {
      final Set<File> inputBundleLocations = new HashSet<File>();
      final TychoProject tychoProject = projectTypes.get(project.getPackaging());
      if (tychoProject instanceof EclipseFeatureProject)
      {
         ArtifactDependencyWalker dependencyWalker = tychoProject.getDependencyWalker(project);
         dependencyWalker.walk(new ArtifactDependencyVisitor()
         {
            @Override
            public void visitPlugin(PluginDescription plugin)
            {
               final File location;

               ReactorProject mavenProject = plugin.getMavenProject();
               if (mavenProject != null)
               {
                  location = mavenProject.getArtifact();
               }
               else
               {
                  location = plugin.getLocation();
               }

               if (location.isDirectory())
               {
                  throw new IllegalStateException("Bundle location is directory, expected jar: " + location);
               }

               inputBundleLocations.add(location);

               super.visitPlugin(plugin);
            }
         });
      }
      else if (tychoProject instanceof BundleProject)
      {
         final ReactorProject mavenProject = DefaultReactorProject.adapt(project);
         final File location = mavenProject.getArtifact();

         if (location.isDirectory())
         {
            throw new IllegalStateException("Bundle location is directory, expected jar: " + location);
         }

         inputBundleLocations.add(location);
      }
      return inputBundleLocations;
   }

   private GAVStrategy newGAVStrategy()
   {
      final GAVStrategyFactory.Request request = new GAVStrategyFactory.Request();
      request.setGroupIdPrefix(groupIdPrefix);
      request.setTrimQualifiers(trimQualifiers);

      if (groupIdMappings != null)
      {
         for (String groupIdMapping : groupIdMappings)
         {
            final String[] split = groupIdMapping.split("=");
            if (split.length != 2)
            {
               throw Exceptions.pipe(new MojoExecutionException("Invalid groupId mapping " + groupIdMapping));
            }
            request.getGroupIdMappings().put(split[0], split[1]);
         }
      }

      if (group3Prefixes != null)
      {
         request.getGroup3Prefixes().addAll(group3Prefixes);
      }
      return gavStrategyFactory.newGAVStrategy(request);
   }

   protected Set<File> getBundleLocationsInBuildScope()
   {
      if (bundleLocationsInBuildScope == null)
      {
         bundleLocationsInBuildScope = determineBundleLocationsInBuildScope();
      }
      return bundleLocationsInBuildScope;
   }

   private State resolveState(final OsgiStateBuilder stateBuilder)
   {
      // TODO report unresolved requirements
      final State state = stateBuilder.getState();
      state.resolve(false);
      return state;
   }

   @SuppressWarnings({ "rawtypes", "unchecked" })
   private void populateRequest(final Mavenizor.Request request)
   {
      final PropertiesMap options = request.getOptions();
      if (this.options != null)
      {
         options.putAll((Map) this.options);
      }

      if (libraryMappings != null)
      {
         for (String libraryMapping : libraryMappings)
         {
            String[] split = libraryMapping.split("=");
            if (split.length != 2)
            {
               throw Exceptions.pipe(new MojoExecutionException("Invalid library mapping " + libraryMapping));
            }
            options.put(split[0], split[1]);
         }
      }

      request.setWorkingDirectory(workingDir.getAbsoluteFile());
      request.setTargetType(determineTargetType());
      request.setGAVStrategy(newGAVStrategy());
      request.setInputFilter(newInputFilter());
      request.setSourceJarResolver(new SourceJarResolver()
      {
         public File resolveSource(BundleDescription bundle)
         {
            final File bundleJar = BundleAdapterFactory.DEFAULT.adapt(bundle, File.class);
            final MavenProject project = getMavenProject(bundleJar);
            if (project != null)
            {
               return getSourceJar(project);
            }
            return null;
         }

         private File getSourceJar(MavenProject project)
         {
            for (Artifact artifact : project.getAttachedArtifacts())
            {
               if ("jar".equals(artifact.getType()) && "sources".equals(artifact.getClassifier()))
               {
                  return artifact.getFile();
               }
            }
            return null;
         }

         private MavenProject getMavenProject(final File bundleJar)
         {
            for (MavenProject project : session.getProjects())
            {
               if (bundleJar.equals(project.getArtifact().getFile()))
               {
                  return project;
               }
            }
            return null;
         }
      });
   }

   protected abstract void processResult(Result result);

   private void addPlatformProperties(final MavenSession session, final OsgiStateBuilder stateBuilder)
   {
      final MavenProject project = session.getCurrentProject();

      final TargetPlatformConfiguration configuration = TychoProjectUtils.getTargetPlatformConfiguration(project);

      final String eeName = configuration.getExecutionEnvironment();
      if (eeName == null)
      {
         stateBuilder.addSystemExecutionEnvironmentProperties();
      }
      else
      {
         stateBuilder.addExecutionEnvironmentProperties(eeName);
      }

      final Set<String> os = new MultiProperty();
      final Set<String> ws = new MultiProperty();
      final Set<String> arch = new MultiProperty();
      for (TargetEnvironment targetEnvironment : configuration.getEnvironments())
      {
         os.add(targetEnvironment.getOs());
         ws.add(targetEnvironment.getWs());
         arch.add(targetEnvironment.getArch());
      }

      final Map<String, Object> targetMap = new HashMap<String, Object>();
      targetMap.put(OsgiStateBuilder.OSGI_OS, os);
      targetMap.put(OsgiStateBuilder.OSGI_WS, ws);
      targetMap.put(OsgiStateBuilder.OSGI_ARCH, arch);
      stateBuilder.addPlatformProperties(targetMap);
   }

   private void addBundles(final OsgiStateBuilder stateBuilder)
   {
      bundleResolver.resolve(session, new Handler()
      {
         public void resolved(File bundleLocation)
         {
            stateBuilder.addBundle(bundleLocation);
         }
      });
   }
}
