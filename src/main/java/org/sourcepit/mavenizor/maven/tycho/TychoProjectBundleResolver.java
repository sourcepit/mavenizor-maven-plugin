/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.tycho;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.tycho.ArtifactDescriptor;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.artifacts.DependencyArtifacts;
import org.eclipse.tycho.artifacts.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolverConfiguration;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.TargetPlatformResolver;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.resolver.DefaultTargetPlatformResolverFactory;
import org.eclipse.tycho.core.resolver.shared.OptionalResolutionAction;
import org.eclipse.tycho.core.utils.TychoProjectUtils;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.maven.BundleResolver;

@Named("tycho-project")
public class TychoProjectBundleResolver implements BundleResolver
{
   @Inject
   private DefaultTargetPlatformResolverFactory targetPlatformResolverLocator;

   public void resolve(final MavenSession session, final Handler handler)
   {
      final MavenProject project = session.getCurrentProject();

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
            return OptionalResolutionAction.REQUIRE;
         }

         public List<Dependency> getExtraRequirements()
         {
            return dependencies;
         }
      };

      DependencyArtifacts dependencyArtifacts = platformResolver.resolveDependencies(session, project, targetPlatform,
         reactorProjects, resolverConfiguration);

      if (dependencyArtifacts == null)
      {
         throw Exceptions.pipe(new MojoExecutionException("Cannot determinate build target platform location"));
      }

      for (ArtifactDescriptor artifact : dependencyArtifacts.getArtifacts(ArtifactKey.TYPE_ECLIPSE_PLUGIN))
      {
         ReactorProject mavenProject = artifact.getMavenProject();
         if (mavenProject == null)
         {
            handler.resolved(artifact.getLocation());
         }
         else
         {
            File projectArtifact = mavenProject.getArtifact(artifact.getClassifier());
            if (projectArtifact == null)
            {
               handler.resolved(artifact.getLocation());
            }
            else
            {
               handler.resolved(projectArtifact);
            }
         }
      }
   }
}
