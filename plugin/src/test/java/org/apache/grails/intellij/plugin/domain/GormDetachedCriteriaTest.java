/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.intellij.plugin.domain;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.apache.grails.intellij.lib.testFramework.GrailsTestCase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToUtilKt;

public class GormDetachedCriteriaTest extends GrailsTestCase {
  @Override
  protected boolean useGrails14() {
    return true;
  }

  public void testRename() throws Exception {
    addDomain("""
                
                class Ddd {
                  String name
                }
                """);

    configureBySimpleGroovyFile("""
                                  
                                  def x = new grails.gorm.DetachedCriteria<Ddd>(Ddd.class).build {
                                    eq "name", "Ivan"
                                    projections {
                                      if (true) {
                                        max("name")
                                      }
                                      eq "name", "1"
                                    }
                                  }
                                  
                                  x.and {
                                    eq "name", "Ivan"
                                  }
                                  
                                  x.eq("name", "Ivan")
                                  
                                  x = x.build({
                                    eq "name", "Ivan"
                                  })
                                  
                                  x.list(sort: 'name', {
                                      gt ""\"name""\", "a"
                                  })
                                  
                                  def z = Ddd.where {
                                      eq "name", "Ivan"
                                  }
                                  
                                  z.build {
                                    or {
                                      eq "name", "Vasya"
                                      or {
                                        eq "name", "Vasya"
                                      }
                                    }
                                  }
                                  
                                  Ddd.findAll [:], {
                                      eq "name", "Ivan"
                                      projections {
                                        if (true) {
                                          max("name")
                                        }
                                        eq "name", "1"
                                      }
                                  }
                                  
                                  Ddd.find {
                                      eq "name<caret>", "Ivan"
                                  }
                                  
                                  def g = new grails.gorm.DetachedCriteria<Ddd>(Ddd.class);
                                  g.updateAll(name: "Sergey")
                                  g.each { d ->
                                    println(d.name)
                                  }
                                  """);

    myFixture.renameElementAtCaret("firstName");

    myFixture.checkResult("""
                            
                            def x = new grails.gorm.DetachedCriteria<Ddd>(Ddd.class).build {
                              eq "firstName", "Ivan"
                              projections {
                                if (true) {
                                  max("firstName")
                                }
                                eq "firstName", "1"
                              }
                            }
                            
                            x.and {
                              eq "firstName", "Ivan"
                            }
                            
                            x.eq("firstName", "Ivan")
                            
                            x = x.build({
                              eq "firstName", "Ivan"
                            })
                            
                            x.list(sort: 'firstName', {
                                gt ""\"firstName""\", "a"
                            })
                            
                            def z = Ddd.where {
                                eq "firstName", "Ivan"
                            }
                            
                            z.build {
                              or {
                                eq "firstName", "Vasya"
                                or {
                                  eq "firstName", "Vasya"
                                }
                              }
                            }
                            
                            Ddd.findAll [:], {
                                eq "firstName", "Ivan"
                                projections {
                                  if (true) {
                                    max("firstName")
                                  }
                                  eq "firstName", "1"
                                }
                            }
                            
                            Ddd.find {
                                eq "firstName", "Ivan"
                            }
                            
                            def g = new grails.gorm.DetachedCriteria<Ddd>(Ddd.class);
                            g.updateAll(firstName: "Sergey")
                            g.each { d ->
                              println(d.firstName)
                            }
                            """);
  }

  /**
   * Below GORM 4 {@code where} is a light dynamic method, whose closure gets the criteria methods from
   * DetachedCriteriaClosureMemberProvider. The delegate GORM 4+ where queries get must not be added on top.
   */
  public void testLightWhereMethodKeepsClosureMembers() {
    addDomain("""
                
                class Ddd {
                  String name
                }
                """);

    PsiFile file = configureBySimpleGroovyFile("""
                                                 
                                                 Ddd.where {
                                                   e<caret>q "name", "Ivan"
                                                 }
                                                 """);

    GrClosableBlock closure = PsiTreeUtil.findChildOfType(file, GrClosableBlock.class);
    assertNull(GrDelegatesToUtilKt.getDelegatesToInfo(closure));

    PsiElement leaf = file.findElementAt(myFixture.getCaretOffset());
    PsiElement target = PsiTreeUtil.getParentOfType(leaf, GrReferenceExpression.class).resolve();
    assertInstanceOf(target, GrLightMethodBuilder.class);
    assertEquals("grails.gorm.DetachedCriteria", ((PsiMethod)target).getContainingClass().getQualifiedName());
  }

  public void testResolveDynamicFinderMethod() {
    PsiFile file = addDomain("""
                               
                               class Ddd {
                                 String firstName;
                                 String lastName;
                               
                                 static {
                                   def criteria = where {
                                     isNotNull("firstName")
                                   }
                               
                                   criteria.findByLastNameAndVersionBetween("Ivanov", 1, 2)
                                 }
                               }
                               """);
    GrailsTestCase.checkResolve(file);
  }

  public void testCompletionDynamicFinders() {
    configureByDomain("""
                        
                        class Ddd {
                          String firstName;
                          String lastName;
                        
                          static {
                            def criteria = where {
                              isNotNull("firstName")
                            }
                        
                            criteria.findByLastNameAnd<caret>
                          }
                        }
                        """);
    checkCompletion("findByLastNameAndVersion", "findByLastNameAndId");
  }
}
