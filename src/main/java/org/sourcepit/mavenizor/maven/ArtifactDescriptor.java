/**
 * Copyright (c) 2012 Sourcepit.org contributors and others. All rights reserved. This program and the accompanying
 * materials are made available under the terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.sourcepit.mavenizor.maven;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.maven.model.Model;

public class ArtifactDescriptor
{
   private Model model;
   private File file;
   private Map<String, File> classifierToFile = new LinkedHashMap<String, File>();

   public Model getModel()
   {
      return model;
   }

   public void setModel(Model model)
   {
      this.model = model;
   }

   public File getFile()
   {
      return file;
   }

   public void setFile(File file)
   {
      this.file = file;
   }

   public Map<String, File> getClassifierToFile()
   {
      return classifierToFile;
   }
}