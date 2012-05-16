/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.sisu.equinox.launching.DefaultEquinoxInstallationDescription;
import org.eclipse.sisu.equinox.launching.EquinoxInstallation;
import org.eclipse.sisu.equinox.launching.EquinoxInstallationDescription;
import org.eclipse.sisu.equinox.launching.EquinoxInstallationFactory;
import org.eclipse.tycho.ArtifactDescriptor;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.artifacts.DependencyArtifacts;
import org.eclipse.tycho.artifacts.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolverConfiguration;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TargetPlatformResolver;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.resolver.DefaultTargetPlatformConfigurationReader;
import org.eclipse.tycho.core.resolver.DefaultTargetPlatformResolverFactory;
import org.eclipse.tycho.core.resolver.shared.OptionalResolutionAction;
import org.eclipse.tycho.core.utils.TychoProjectUtils;
import org.eclipse.tycho.resolver.TychoDependencyResolver;
import org.slf4j.Logger;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.common.utils.lang.PipedException;

/**
 * @requiresDependencyResolution compile
 * @goal mavenize
 * @phase package
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorMojo extends AbstractMavenizorMojo
{
   @Inject
   private Logger logger;

   @Inject
   private DefaultTargetPlatformResolverFactory targetPlatformResolverLocator;

   @Inject
   private EquinoxInstallationFactory installationFactory;

   /** @parameter expression="${session}" */
   private MavenSession session;

   /** @parameter expression="${project}" */
   private MavenProject project;

   /** @parameter default-value="${project.build.directory}/foo" */
   private File work;

   @Inject
   private TychoDependencyResolver resolver;

   /**
    * Additional target platform dependencies.
    * 
    * @parameter
    */
   private Dependency[] dependencies;

   @Inject
   private DefaultTargetPlatformConfigurationReader configurationReader;


   @Override
   protected void doExecute() throws PipedException
   {
      logger.info("Hello :-)");
      work.mkdirs();

      try
      {
         createEclipseInstallation(project, false, DefaultReactorProject.adapt(session));
      }
      catch (MojoExecutionException e)
      {
         throw Exceptions.pipe(e);
      }

      // useTargetPlatformConfiguration();

      logger.info("awesome!");
   }

   private void useTargetPlatformConfiguration()
   {
      TargetPlatformResolver platformResolver = targetPlatformResolverLocator.lookupPlatformResolver(project);

      TargetPlatformConfiguration configuration = TychoProjectUtils.getTargetPlatformConfiguration(project);

      final ArrayList<Dependency> dependencies = new ArrayList<Dependency>();

      dependencies.addAll(configuration.getDependencyResolverConfiguration().getExtraRequirements());

      // TODO 364134 re-use target platform from dependency resolution
      List<ReactorProject> reactorProjects = DefaultReactorProject.adapt(session);
      TargetPlatform targetPlatform = platformResolver.computeTargetPlatform(session, project, reactorProjects);

      final DependencyResolverConfiguration resolverConfiguration = new DependencyResolverConfiguration()
      {
         public OptionalResolutionAction getOptionalResolutionAction()
         {
            return OptionalResolutionAction.IGNORE;
         }

         public List<Dependency> getExtraRequirements()
         {
            return dependencies;
         }
      };

      DependencyArtifacts testRuntimeArtifacts = platformResolver.resolveDependencies(session, project, targetPlatform,
         reactorProjects, resolverConfiguration);

      if (testRuntimeArtifacts == null)
      {
         throw Exceptions.pipe(new MojoExecutionException(
            "Cannot determinate build target platform location -- not executing tests"));
      }

      EquinoxInstallationDescription testRuntime = new DefaultEquinoxInstallationDescription();


      for (ArtifactDescriptor artifact : testRuntimeArtifacts.getArtifacts(ArtifactKey.TYPE_ECLIPSE_PLUGIN))
      {
         // note that this project is added as directory structure rooted at project basedir.
         // project classes and test-classes are added via dev.properties file (see #createDevProperties())
         // all other projects are added as bundle jars.
         ReactorProject otherProject = artifact.getMavenProject();
         if (otherProject != null)
         {
            if (otherProject.sameProject(project))
            {
               testRuntime.addBundle(artifact.getKey(), project.getBasedir());
               continue;
            }
            File file = otherProject.getArtifact(artifact.getClassifier());
            if (file != null)
            {
               testRuntime.addBundle(artifact.getKey(), file);
               continue;
            }
         }
         testRuntime.addBundle(artifact);
      }
      work.mkdirs();

      EquinoxInstallation installation = installationFactory.createInstallation(testRuntime, work);
   }

   private EquinoxInstallation createEclipseInstallation(MavenProject project, boolean includeReactorProjects,
      List<ReactorProject> reactorProjects) throws MojoExecutionException
   {
      TargetPlatformResolver platformResolver = targetPlatformResolverLocator.lookupPlatformResolver(project);

      final List<Dependency> dependencies = getBasicDependencies();
      if (this.dependencies != null)
      {
         dependencies.addAll(Arrays.asList(this.dependencies));
      }

      TargetPlatform targetPlatform = platformResolver.computeTargetPlatform(session, project, reactorProjects);

      DependencyResolverConfiguration resolverConfiguration = new DependencyResolverConfiguration()
      {
         public OptionalResolutionAction getOptionalResolutionAction()
         {
            return OptionalResolutionAction.REQUIRE;
         }

         public List<Dependency> getExtraRequirements()
         {
            return dependencies;
         }
      };

      // igorf: I am not convinced that using project dependencies is expected/desired here. Original usecase
      // for eclipse-run mojo was to build documentation index, which most likely does not require project
      // dependencies, but some other unrelated set of bundles.

      DependencyArtifacts runtimeArtifacts = platformResolver.resolveDependencies(session, project, targetPlatform,
         reactorProjects, resolverConfiguration);

      EquinoxInstallationDescription installationDesc = new DefaultEquinoxInstallationDescription();

      for (ArtifactDescriptor artifact : runtimeArtifacts.getArtifacts(ArtifactKey.TYPE_ECLIPSE_PLUGIN))
      {
         installationDesc.addBundle(artifact);
      }

      return installationFactory.createInstallation(installationDesc, work);
   }

   private List<Dependency> getBasicDependencies()
   {
      ArrayList<Dependency> result = new ArrayList<Dependency>();

      result.add(newBundleDependency("org.eclipse.osgi"));
      result.add(newBundleDependency(EquinoxInstallationDescription.EQUINOX_LAUNCHER));
      result.add(newBundleDependency("org.eclipse.core.runtime"));

      return result;
   }

   private Dependency newBundleDependency(String bundleId)
   {
      Dependency ideapp = new Dependency();
      ideapp.setArtifactId(bundleId);
      ideapp.setType(ArtifactKey.TYPE_ECLIPSE_PLUGIN);
      return ideapp;
   }
}
