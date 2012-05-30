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
public class DefaultConverterFactory implements ConverterFactory
{
   public Converter newConverter(Request request)
   {
      final List<SnapshotRule> snapshotRules = new ArrayList<SnapshotRule>();
      if (request.isUseDefaultSnapshotRules())
      {
         Collections.addAll(snapshotRules, ConverterFactory.DEFAULT_SNAPSHOT_RULES);
      }
      snapshotRules.addAll(request.getAdditionalSnapshotRules());
      return new DefaultConverter(snapshotRules);
   }
}
