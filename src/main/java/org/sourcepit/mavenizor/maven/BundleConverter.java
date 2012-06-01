/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.util.Collection;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.sourcepit.common.utils.props.PropertiesMap;
import org.sourcepit.mavenizor.maven.converter.GAVStrategy;

public interface BundleConverter
{
   Collection<ArtifactDescription> toMavenArtifacts(BundleDescription bundle, final List<Dependency> dependencies,
      GAVStrategy converter, PropertiesMap options);
}
