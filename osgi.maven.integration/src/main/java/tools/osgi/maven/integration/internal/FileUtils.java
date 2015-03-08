package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
   private static final Logger LOG = LoggerFactory.getLogger( FileUtils.class );

   public static String md5HexForDir( File dirToHash ) {
      return md5HexForDir( dirToHash, true );
   }

   public static String md5HexForDir( File dirToHash, boolean includeHiddenFiles ) {
      Validate.isTrue( dirToHash.isDirectory(), "File must be directory" );
      final Vector<FileInputStream> fileStreams = new Vector<FileInputStream>();

      LOG.info( "Found files for hashing:" );
      collectInputStreams( dirToHash, fileStreams, includeHiddenFiles );

      final SequenceInputStream seqStream = new SequenceInputStream( fileStreams.elements() );

      try {
         String md5Hash = DigestUtils.md5Hex( seqStream );
         seqStream.close();
         return md5Hash;
      }
      catch( IOException e ) {
         throw new RuntimeException( "Error reading files to hash in " + dirToHash.getAbsolutePath(), e );
      }
   }

   private static void collectInputStreams( File dir, List<FileInputStream> foundStreams, boolean includeHiddenFiles ) {
      final File[] fileList = dir.listFiles();
      Arrays.sort( fileList, // Need in reproducible order
            new Comparator<File>() {
               public int compare( File f1, File f2 ) {
                  return f1.getName().compareTo( f2.getName() );
               }
            } );
      for( File f : fileList ) {
         if( !includeHiddenFiles && f.getName().startsWith( "." ) ) {
            // Skip it
         }
         else if( f.isDirectory() ) {
            collectInputStreams( f, foundStreams, includeHiddenFiles );
         }
         else {
            try {
               LOG.info( "\t" + f.getAbsolutePath() );
               foundStreams.add( new FileInputStream( f ) );
            }
            catch( FileNotFoundException e ) {
               throw new AssertionError( e.getMessage() + ": file should never not be found!" );
            }
         }
      }
   }
}
