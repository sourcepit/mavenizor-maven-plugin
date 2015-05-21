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

package org.sourcepit.mavenizor.maven.tycho;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.tycho.ArtifactDescriptor;
import org.eclipse.tycho.ArtifactKey;
import org.eclipse.tycho.PackagingType;
import org.eclipse.tycho.ReactorProject;
import org.eclipse.tycho.artifacts.DependencyArtifacts;
import org.eclipse.tycho.artifacts.TargetPlatform;
import org.eclipse.tycho.core.DependencyResolver;
import org.eclipse.tycho.core.DependencyResolverConfiguration;
import org.eclipse.tycho.core.TargetPlatformConfiguration;
import org.eclipse.tycho.core.osgitools.DefaultReactorProject;
import org.eclipse.tycho.core.resolver.DefaultDependencyResolverFactory;
import org.eclipse.tycho.core.resolver.shared.OptionalResolutionAction;
import org.eclipse.tycho.core.utils.TychoProjectUtils;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.maven.BundleResolver;

@Named("tycho-project")
public class TychoProjectBundleResolver implements BundleResolver {
   @Inject
   private DefaultDependencyResolverFactory targetPlatformResolverLocator;

   @Inject
   private TychoSourceIUResolver sourceResolver;

   public void resolve(final MavenSession session, final Handler handler) {
      final MavenProject project = session.getCurrentProject();

      DependencyResolver platformResolver = targetPlatformResolverLocator.lookupDependencyResolver(project);

      TargetPlatformConfiguration configuration = TychoProjectUtils.getTargetPlatformConfiguration(project);

      final ArrayList<Dependency> dependencies = new ArrayList<Dependency>();

      dependencies.addAll(configuration.getDependencyResolverConfiguration().getExtraRequirements());

      // TODO 364134 re-use target platform from dependency resolution
      List<ReactorProject> reactorProjects = DefaultReactorProject.adapt(session);
      TargetPlatform targetPlatform = platformResolver.computePreliminaryTargetPlatform(session, project,
         reactorProjects);

      final DependencyResolverConfiguration resolverConfiguration = new DependencyResolverConfiguration() {
         public OptionalResolutionAction getOptionalResolutionAction() {
            return OptionalResolutionAction.REQUIRE;
         }

         public List<Dependency> getExtraRequirements() {
            return dependencies;
         }
      };

      DependencyArtifacts dependencyArtifacts = platformResolver.resolveDependencies(session, project, targetPlatform,
         reactorProjects, resolverConfiguration);

      if (dependencyArtifacts == null) {
         throw Exceptions.pipe(new MojoExecutionException("Cannot determinate build target platform location"));
      }

      final Set<String> sourceTargetBundles = new LinkedHashSet<String>();

      for (ArtifactDescriptor artifact : dependencyArtifacts.getArtifacts(PackagingType.TYPE_ECLIPSE_PLUGIN)) {
         ReactorProject mavenProject = artifact.getMavenProject();
         if (mavenProject == null) {
            handler.resolved(artifact.getLocation());
            final ArtifactKey key = artifact.getKey();
            sourceTargetBundles.add(key.getId() + "_" + key.getVersion()); // try to resolve sources for non-project
         }
         else {
            File projectArtifact = mavenProject.getArtifact(artifact.getClassifier());
            if (projectArtifact == null) {
               handler.resolved(artifact.getLocation());
            }
            else {
               handler.resolved(projectArtifact);
            }
         }
      }

      if (!sourceTargetBundles.isEmpty()) {
         sourceResolver.resolveSources(session, targetPlatform, sourceTargetBundles, handler);
      }
   }
}
