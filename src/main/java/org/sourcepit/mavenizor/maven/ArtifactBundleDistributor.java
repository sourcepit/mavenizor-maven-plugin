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
import javax.inject.Named;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.ModelWriter;
import org.apache.maven.repository.RepositorySystem;
import org.sourcepit.common.maven.model.MavenArtifact;
import org.sourcepit.common.utils.io.IOOperation;
import org.sourcepit.mavenizor.ArtifactBundle;
import org.sourcepit.mavenizor.maven.converter.ConvertedArtifact;

@Named
public class ArtifactBundleDistributor
{
   @Inject
   private RepositorySystem repositorySystem;

   @Inject
   private ArtifactRepositoryLayout repositoryLayout;

   @Inject
   private ModelWriter modelWriter;

   interface DistributionHandler
   {
      void distribute(Artifact artifact, boolean forceOverwrite);
   }

   public void distribute(File workingDir, ArtifactBundle artifactBundle, DistributionHandler distributor,
      boolean forceOverwrite)
   {
      boolean pomDistributed = false;
      for (ConvertedArtifact cArtifact : artifactBundle.getArtifacts())
      {
         if (cArtifact.isMavenized())
         {
            MavenArtifact mavenArtifact = cArtifact.getMavenArtifact();
            final Model pom = artifactBundle.getPom();
            if (!pomDistributed)
            {
               distributePom(workingDir, pom, distributor, forceOverwrite);
               pomDistributed = true;
            }
            final Artifact artifact = createArtifact(pom, mavenArtifact.getClassifier(), mavenArtifact.getType());
            artifact.setFile(mavenArtifact.getFile());
            distribute(distributor, artifact, forceOverwrite);
         }
      }
   }

   private void distributePom(File workinDir, final Model pom, DistributionHandler distributor, boolean forceOverwrite)
   {
      final Artifact pomArtifact = createArtifact(pom, "pom");

      final File pomFile = new File(workinDir, repositoryLayout.pathOf(pomArtifact));
      new IOOperation<OutputStream>(buffOut(fileOut(pomFile, true)))
      {
         @Override
         protected void run(OutputStream outputStream) throws IOException
         {
            modelWriter.write(outputStream, null, pom);
         }
      }.run();
      pomArtifact.setFile(pomFile);
      distribute(distributor, pomArtifact, forceOverwrite);
   }

   private void distribute(DistributionHandler distributor, Artifact artifact, boolean forceOverwrite)
   {
      distributor.distribute(artifact, forceOverwrite);
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
