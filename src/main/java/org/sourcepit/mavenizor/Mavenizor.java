/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.slf4j.Logger;

@Named
public class Mavenizor
{
   @Inject
   private Logger log;
   
   public void mavenize(State state)
   {
      for (BundleDescription bundle : state.getBundles())
      {
         log.info(bundle.toString());
      }
   }
}
