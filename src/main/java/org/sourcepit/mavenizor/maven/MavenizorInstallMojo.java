/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import static org.sourcepit.common.utils.io.IOResources.buffOut;
import static org.sourcepit.common.utils.io.IOResources.fileOut;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.installer.ArtifactInstallationException;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.repository.RepositorySystem;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.common.utils.lang.Exceptions;
import org.sourcepit.mavenizor.Mavenizor.Result;

/**
 * @requiresDependencyResolution compile
 * @goal install-bundles
 * @phase install
 * @author Bernd Vogt <bernd.vogt@sourcepit.org>
 */
public class MavenizorInstallMojo extends MavenizorMojo
{
   @Inject
   private ArtifactInstaller installer;

   @Inject
   private RepositorySystem repositorySystem;

   @Inject
   private ArtifactRepositoryLayout repositoryLayout;

   @Inject
   private ModelWriter modelWriter;

   /** @parameter expression="${localRepository}" */
   protected ArtifactRepository localRepository;

   @Override
   protected void postProcess(Result result)
   {
      try
      {
         localRepository = repositorySystem.createLocalRepository(new File(work, "repo"));
      }
      catch (InvalidRepositoryException e)
      {
         throw Exceptions.pipe(e);
      }

      for (ArtifactBundle artifactBundle : result.getGAVToMavenArtifactBundleMap().values())
      {
         final Model pom = artifactBundle.getPom();

         final Artifact pomArtifact = createArtifact(pom, "pom");

         final File pomFile = new File(work, repositoryLayout.pathOf(pomArtifact));
         new IOOperation<OutputStream>(buffOut(fileOut(pomFile, true)))
         {
            @Override
            protected void run(OutputStream outputStream) throws IOException
            {
               modelWriter.write(outputStream, null, pom);
            }
         }.run();
         install(pomFile, pomArtifact);

         for (MavenArtifact mavenArtifact : artifactBundle.getArtifacts())
         {
            final Artifact artifact = createArtifact(pom, mavenArtifact.getClassifier(), mavenArtifact.getType());
            install(mavenArtifact.getFile(), artifact);

         }
      }
   }

   private void install(final File file, final Artifact artifact)
   {
      try
      {
         installer.install(file, artifact, localRepository);
      }
      catch (ArtifactInstallationException e)
      {
         throw Exceptions.pipe(e);
      }
   }

   private Artifact createArtifact(final Model model, String packaging)
   {
      return repositorySystem.createArtifact(model.getGroupId(), model.getArtifactId(), model.getVersion(), packaging);
   }

   private Artifact createArtifact(final Model model, String classifier, String type)
   {
      return repositorySystem.createArtifactWithClassifier(model.getGroupId(), model.getArtifactId(),
         model.getVersion(), type, classifier);
   }
}
