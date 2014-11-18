package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Resource;

public class ObrUtils {

   public static Resource createResource( DataModelHelper dataModelHelper, final File bundleFolder ) {
      return createResource( dataModelHelper, bundleFolder, bundleFolder.toURI() );
   }

   public static boolean isOsgiBundle( final File bundleFolder ) {
      final File manifestFile = new File( bundleFolder, JarFile.MANIFEST_NAME );
      return manifestFile.exists();
   }

   public static Resource createResource( DataModelHelper dataModelHelper, final File bundleFolder, final URI uri ) {
      try {
         final File manifestFile = new File( bundleFolder, JarFile.MANIFEST_NAME );
         if( !manifestFile.exists() ) {
            throw new IllegalArgumentException( "The specified folder is not a valid bundle (can't read manifest): " + bundleFolder );
         }
         final Manifest manifest = new Manifest( new FileInputStream( manifestFile ) );
         final Resource result = dataModelHelper.createResource( manifest.getMainAttributes() );
         //         if( result != null ) {
         //            result.put( Resource.SIZE, Long.toString( getFolderSize( bundleFolder ) ), null );
         //            result.put( Resource.URI, uri.toASCIIString(), null );
         //         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error creating OBR resource for folder: %s", bundleFolder.getAbsolutePath() ), exception );
      }
   }

   /**
    * Calculates the size of a folder in bytes
    * @param folder Folder to calculate size of
    * @return Size of folder
    */
   public static long getFolderSize( File folder ) {
      long foldersize = 0;
      final File[] filelist = folder.listFiles();
      for( int i = 0; i < filelist.length; i++ ) {
         if( filelist[i].isDirectory() ) {
            foldersize += getFolderSize( filelist[i] );
         }
         else {
            foldersize += filelist[i].length();
         }
      }
      return foldersize;
   }
}
