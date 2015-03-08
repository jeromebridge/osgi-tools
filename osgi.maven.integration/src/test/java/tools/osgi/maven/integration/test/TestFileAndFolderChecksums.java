package tools.osgi.maven.integration.test;

import java.io.File;
import java.io.InputStream;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Test;

import tools.osgi.maven.integration.internal.FileUtils;

public class TestFileAndFolderChecksums {

   @Test
   public void testFile() throws Exception {
      final InputStream fis = getClass().getResourceAsStream( "/eclipse-location-file" );
      final String md5 = DigestUtils.md5Hex( fis );
      fis.close();

      System.out.println( md5 );
   }

   @Test
   public void testFolder() throws Exception {
      final String md5 = FileUtils.md5HexForDir( new File( "." ) );

      System.out.println( md5 );
   }

}
