package tools.osgi.maven.integration.test;

import java.io.File;
import java.io.FileOutputStream;

import tools.osgi.maven.integration.api.JarBuilder;

public class TestCreateJarFile {

   // Fails on continuous build server
   // @Test
   public void test1() throws Exception {
      final File jarFile = File.createTempFile( "temp", ".jar" );
      final FileOutputStream fos = new FileOutputStream( jarFile );
      System.out.println( "Jar File: " + jarFile.toURI().toURL().toExternalForm() );

      final JarBuilder builder = new JarBuilder();
      builder.add( new File( "/home/developer/git/yet-another-admin-system/yaas-ws/bin/maven/classes" ) );
      builder.build( fos );
   }

}
