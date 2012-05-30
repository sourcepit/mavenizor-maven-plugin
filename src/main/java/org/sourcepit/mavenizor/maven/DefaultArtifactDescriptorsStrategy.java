/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Named;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.mavenizor.maven.converter.Converter;

@Named
public class DefaultArtifactDescriptorsStrategy implements ArtifactDescriptorsStrategy
{
   public Collection<ArtifactDescriptor> determineArtifactDescriptors(BundleDescription bundle,
      List<Dependency> dependencies, Converter converter)
   {
      final Model model = new Model();
      model.setGroupId(converter.deriveGroupId(bundle));
      model.setArtifactId(converter.deriveArtifactId(bundle));
      model.setVersion(converter.deriveMavenVersion(bundle));
      model.setDependencies(dependencies);

      final ArtifactDescriptor descriptor = new ArtifactDescriptor();
      descriptor.setModel(model);
      descriptor.setFile(new File(bundle.getLocation()));

      final Collection<ArtifactDescriptor> descriptors = new ArrayList<ArtifactDescriptor>();
      descriptors.add(descriptor);
      return descriptors;
   }
}
