/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven.converter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Named;

@Named
public class DefaultGAVStrategyFactory implements GAVStrategyFactory
{
   public GAVStrategy newGAVStrategy(Request request)
   {
      final List<SnapshotRule> snapshotRules = new ArrayList<SnapshotRule>();
      if (request.isUseDefaultSnapshotRules())
      {
         Collections.addAll(snapshotRules, GAVStrategyFactory.DEFAULT_SNAPSHOT_RULES);
      }
      snapshotRules.addAll(request.getAdditionalSnapshotRules());
      return new DefaultGAVStrategy(snapshotRules);
   }
}
