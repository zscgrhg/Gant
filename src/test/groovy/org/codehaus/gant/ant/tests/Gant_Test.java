//  Gant -- A Groovy build framework based on scripting Ant tasks.
//
//  Copyright © 2008 Russel Winder
//
//  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software distributed under the License is
//  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//  implied. See the License for the specific language governing permissions and limitations under the
//  License.

package org.codehaus.gant.ant.tests ;

import java.io.BufferedReader ;
import java.io.File ;
import java.io.IOException ;
import java.io.InputStreamReader ;

import org.apache.tools.ant.BuildException ;
import org.apache.tools.ant.Project ;
import org.apache.tools.ant.ProjectHelper ;

import junit.framework.TestCase ;

/**
 *  Unit tests for the Gant Ant task.  In order to test things appropriately this test must be initiated
 *  without any of the Groovy, Gant or related jars in the class path.  Also of course it must be a JUnit
 *  test with no connection to Groovy or Gant.
 *
 *  @author Russel Winder
 */
public class Gant_Test extends TestCase {
  private final String path = "src/test/groovy/org/codehaus/gant/ant/tests" ;
  private final File antFile = new File ( path + "/gantTest.xml" ) ;
  private Project project ;

  //  This variable is assigned in the Gant script hence the public static.
  public static String returnValue ;
  
  protected void setUp ( ) throws Exception {
    super.setUp ( ) ;
    project = new Project ( ) ;
    //  The Ant jar is not on the classpath of the class loader :-(
    project.init ( ) ;
    ProjectHelper.getProjectHelper ( ).parse ( project , antFile ) ;
    returnValue = "" ;
  }
  
  public void testDefaultTarget ( ) {
    project.executeTarget ( "gantTestDefaultTarget" ) ;
    assertEquals ( "A test target in the default file." , returnValue ) ;
  }
  
  public void testNamedTarget ( ) {
    project.executeTarget ( "gantTestNamedTarget" ) ;
    assertEquals ( "Another target in the default file." , returnValue ) ;
  }

  public void testMissingGantfile ( ) {
    try { project.executeTarget ( "missingGantfile" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Gantfile does not exist." , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  /**
   *  Test the behaviour of a missing target in the Ant XML file.
   */
  public void testUnknownTarget ( ) {
    try { project.executeTarget ( "blahBlahBlahBlah" ) ; }
    catch ( final BuildException be ) {
      assertEquals ( "Target \"blahBlahBlahBlah\" does not exist in the project \"Gant Ant Task Test\". " , be.getMessage ( ) ) ;
      return ;
    }
    fail ( "Should have got a BuildException." ) ;
  }
  /**
   *  Test for the taskdef-related verify error problem.  Whatever it was supposed to do it passes now,
   *  2008-04-14.
   */
  public void testTaskdef ( ) {
    project.executeTarget ( "gantTaskdef" ) ;
    assertEquals ( "OK." , returnValue ) ;
  }
  /*
   *  Run ant explicitly in a separate process.  Return the string that results.
   */
  private String runAnt ( final String xmlFile , final int expectedReturnCode ) {
    final ProcessBuilder pb = new ProcessBuilder ( "ant" , "-f" , xmlFile ) ;
    try {
      final StringBuilder sb = new StringBuilder ( ) ;
      final Process p = pb.start ( ) ;  //  Could throw an IOException hence the try block.
      final BufferedReader br = new BufferedReader ( new InputStreamReader ( p.getInputStream ( ) ) ) ;
      final Thread readThread = new Thread ( ) {
          public void run ( ) {
            try {
              while ( true ) {
                final String line = br.readLine ( ) ;  //  Could throw an IOException hence the try block.
                if ( line == null ) { break ; }
                sb.append ( line ) ;
                sb.append ( System.getProperty ( "line.separator" ) ) ;
              }
            }
            catch ( final IOException ioe ) { fail ( "Got an IOException reading a line in the read thread." ) ; }
          }
        } ;
      readThread.start ( ) ;
      try { assertEquals ( expectedReturnCode , p.waitFor ( ) ) ; }
      catch ( final InterruptedException ie ) { fail ( "Got an InterruptedException waiting for the Ant process to finish." ) ; }
      try { readThread.join ( ) ;}
      catch ( final InterruptedException ie ) { fail ( "Got an InterruptedException waiting for the read thread to terminate." ) ; }
      return sb.toString ( ) ;
    }
    catch ( final IOException ioe ) { fail ( "Got an IOException from starting the process." ) ; }
    //  Keep the compiler happy, it doesn't realize that execution cannot get here.
    return null ;
  }
  /**
   *  Test stemming from GANT-19 and relating to ensuring the right classpath when loading the Groovyc Ant
   *  task.
   */
  public void testRunningAntFromShell ( ) {
    //
    //  This Ant task actually fails always and so the return code is 1.  If the Groovyc class cannot be
    //  loaded then the result is a message:
    //
    //    : taskdef class org.codehaus.groovy.ant.Groovyc cannot be found
    //
    //  whereas if it succeeds the message is more like:
    //
    //    [gant] Error evaluating Gantfile: startup failed, build_gant: 15: unable to resolve class org.codehaus.gant.ant.tests.Gant_Test
    //    [gant]  @ line 15, column 1.
    //    [gant] 1 error
    //    [gant]
    // 
    //  If Gant is compiled against Groovy 1.6-beta-1 then there is an infinite recursion in the metaclass
    //  system emanating from InvokerHelper as the Gant Ant task is started leading to a StackOverflowError,
    //  and so the correct output is not received.
    //
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: " ) ;
    sb.append ( path ) ;
    sb.append ( "/gantTest.xml\n\n-initializeWithGroovyHome:\n\n-initializeNoGroovyHome:\n\ngantTestDefaultTarget:\n     [gant] Error evaluating Gantfile: startup failed, build_gant: 15: unable to resolve class org.codehaus.gant.ant.tests.Gant_Test\n     [gant]  @ line 15, column 1.\n     [gant] 1 error\n     [gant] \n" ) ;
    assertEquals ( sb.toString ( ) , runAnt ( path + "/gantTest.xml" , 1 ) ) ;
  }
  /*
   *  The following test is based on the code presented in email exchanges on the Groovy developer list by
   *  Chris Miles.  cf.  GANT-50.
   *
   *  TODO:  Fix the case of creating a Gant object in a Groovy Ant Task script.
   */
  //  This test passes when tried locally on all machines but fails on Codehaus Bamboo and Canoo CruiseControl.
  public void XXX_testBasedirInSubdir ( ) {
    final String pathToDirectory = System.getProperty ( "user.dir" )  + "/" + path ;
    final StringBuilder sb = new StringBuilder ( ) ;
    sb.append ( "Buildfile: src/test/groovy/org/codehaus/gant/ant/tests/basedir.xml\n     [echo] basedir::ant basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    sb.append ( "\n\n-initializeWithGroovyHome:\n\n-initializeNoGroovyHome:\n\nbuild:\n   [groovy] basedir::groovy basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    sb.append ( "\n   [groovy] basedir::gant basedir=" ) ;
    sb.append ( System.getProperty ( "user.dir" ) ) ; //  Should be sb.append ( pathToDirectory ) ;
    sb.append ( "\n     [gant] basedir::gant basedir=" ) ;
    sb.append ( pathToDirectory ) ;
    sb.append ( "\n\nBUILD SUCCESSFUL\n\n" ) ;
    assertEquals ( sb.toString ( ) , runAnt ( path + "/basedir.xml" , 0 ).replaceFirst ( "Total time: [0-9]*.*" , "" ) ) ;
  }
}
