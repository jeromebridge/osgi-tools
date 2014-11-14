package tools.osgi.maven.integration.test;

import java.io.InputStream;
import java.io.ObjectInputStream;

public class TestLoadEclipseLocationFile {

   // NOT WORKING
   //@Test
   public void testLoadFile() throws Exception {
      // Fixture
      final InputStream is = getClass().getResourceAsStream( "/eclipse-location-file" );

      // Call
      final ObjectInputStream ois = new ObjectInputStream( is );
      ois.readObject();
   }
}
