/*
 * Copyright (C) 2013 Bosch Software Innovations GmbH. All rights reserved.
 */

package org.sourcepit.mavenizor.maven;
public class RequirementFilter
{
   private String bundle, permitted, erase;

   public String getBundle()
   {
      return bundle;
   }

   public void setBundle(String bundle)
   {
      this.bundle = bundle;
   }

   public String getPermitted()
   {
      return permitted;
   }

   public void setPermitted(String permitted)
   {
      this.permitted = permitted;
   }

   public String getErase()
   {
      return erase;
   }

   public void setErase(String erase)
   {
      this.erase = erase;
   }


}
