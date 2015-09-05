package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.felix.bundlerepository.DataModelHelper;
import org.apache.felix.bundlerepository.Resource;
import org.osgi.framework.Constants;

public class ObrUtils {

   public static Resource createResource( DataModelHelper dataModelHelper, final File bundleFolder ) {
      return createResource( dataModelHelper, bundleFolder, bundleFolder.toURI() );
   }

   public static boolean isOsgiBundle( final File bundleFolder ) {
      try {
         final File manifestFile = new File( bundleFolder, JarFile.MANIFEST_NAME );
         boolean result = manifestFile.exists();
         if( result ) {
            final Manifest manifest = new Manifest( new FileInputStream( manifestFile ) );
            result = manifest.getMainAttributes().getValue( Constants.BUNDLE_SYMBOLICNAME ) != null;
         }
         return result;
      }
      catch( Throwable exception ) {
         throw new RuntimeException( String.format( "Failed checking if folder: %s was valid OSGi bundle", bundleFolder.getAbsolutePath() ), exception );
      }
   }

   public static Resource createResource( DataModelHelper dataModelHelper, final File bundleFolder, final URI uri ) {
      try {
         final File manifestFile = new File( bundleFolder, JarFile.MANIFEST_NAME );
         if( !manifestFile.exists() ) {
            throw new IllegalArgumentException( "The specified folder is not a valid bundle (can't read manifest): " + bundleFolder );
         }
         @SuppressWarnings("unused")
         final Manifest manifest = new Manifest( new FileInputStream( manifestFile ) );

         // final Resource result = dataModelHelper.createResource( manifest.getMainAttributes() );
         final Resource result = dataModelHelper.createResource( uri.toURL() );
         //         if( result != null ) {
         //            result.put( Resource.SIZE, Long.toString( getFolderSize( bundleFolder ) ), null );
         //            result.put( Resource.URI, uri.toASCIIString(), null );
         //         }
         return result;
      }
      catch( Throwable exception ) {
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
