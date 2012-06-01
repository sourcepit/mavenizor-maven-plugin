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

public class ArtifactDescription
{
   private boolean deploy = true;
   private Model model;
   private String classifier;
   private String type = "jar";
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

   public boolean isDeploy()
   {
      return deploy;
   }

   public void setDeploy(boolean deploy)
   {
      this.deploy = deploy;
   }

   public String getClassifier()
   {
      return classifier;
   }

   public void setClassifier(String classifier)
   {
      this.classifier = classifier;
   }

   public String getType()
   {
      return type;
   }

   public void setType(String type)
   {
      this.type = type;
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