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
public enum ConvertionDirective {
   AUTO_DETECT, MAVENIZE, OMIT, IGNORE, REPLACE;

   private final String literal;

   private ConvertionDirective() {
      this.literal = name().toLowerCase();
   }

   public final String literal() {
      return literal;
   }

   public static ConvertionDirective valueOfLiteral(String literal) {
      for (ConvertionDirective mode : values()) {
         if (mode.literal().equals(literal)) {
            return mode;
         }
      }
      return null;
   }
}