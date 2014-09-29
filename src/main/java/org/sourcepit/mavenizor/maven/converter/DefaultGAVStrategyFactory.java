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
      return new DefaultGAVStrategy(snapshotRules, request.getGroupIdPrefix(), request.isTrimQualifiers(),
         request.getGroup3Prefixes(), request.getGroupIdMappings());
   }
}
