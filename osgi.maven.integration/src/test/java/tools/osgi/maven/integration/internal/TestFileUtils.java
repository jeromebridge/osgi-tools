package tools.osgi.maven.integration.internal;

import java.io.File;

import org.junit.Test;

public class TestFileUtils {

   
   
   @Test
   public void testPathForUri() throws Exception {
      // Fixture
      final String path = "C:\\users\\administrator\\git\\yet-another-admin-system-master\\spring-aspects-osgi\\bin\\maven\\classes";
      // Windows Output: file:/C:/users/administrator/git/yet-another-admin-system-master/spring-aspects-osgi/bin/maven/classes/

      // Call
      final File file = new File( path );
      final String result = FileUtils.getPathForUri( file );

      // Assert
      System.out.println( file.toURI().toASCIIString() );
      System.out.println( result );
   }
}
