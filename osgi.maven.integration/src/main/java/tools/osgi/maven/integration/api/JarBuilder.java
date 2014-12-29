package tools.osgi.maven.integration.api;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class JarBuilder {
   public static class Source {
      private File source;
      private String targetPath;

      public Source( File source, String targetPath ) {
         setSource( source );
         setTargetPath( targetPath );
      }

      public File getSource() {
         return source;
      }

      public String getTargetPath() {
         return targetPath;
      }

      public void setSource( File source ) {
         this.source = source;
      }

      public void setTargetPath( String targetPath ) {
         this.targetPath = targetPath;
      }

   }

   private List<Source> sources = new ArrayList<JarBuilder.Source>();

   public void add( File source ) {
      add( source, "/" );
   }

   public void add( File source, String targetPath ) {
      sources.add( new Source( source, targetPath ) );
   }

   public void build( OutputStream stream ) {
      try {
         final Manifest manifest = getManifestFromSources();
         final JarOutputStream target = new JarOutputStream( stream, manifest );
         for( Source source : sources ) {
            add( source.getSource(), target, source.getTargetPath() );
         }
         target.close();
      }
      catch( Throwable exception ) {
         throw new RuntimeException( "Error building jar file", exception );
      }
   }

   @SuppressWarnings("unused")
   private void add( File source, JarOutputStream target ) throws IOException {
      add( source, target, "/" );
   }

   private void add( File source, JarOutputStream target, String targetPath ) throws IOException {
      BufferedInputStream in = null;
      try {
         if( source.isDirectory() ) {
            // String name = source.getPath().replace( "\\", "/" );
            String name = ( targetPath != null ? targetPath : "/" ).replace( "\\", "/" );
            if( !name.isEmpty() ) {
               if( !name.endsWith( "/" ) ) {
                  name += "/";
               }
               if( !"/".equals( name ) ) {
                  final JarEntry entry = new JarEntry( name );
                  entry.setTime( source.lastModified() );
                  target.putNextEntry( entry );
                  target.closeEntry();
               }
            }
            for( File nestedFile : source.listFiles() ) {
               String relativePath = getRelativePath( source, nestedFile );
               relativePath = relativePath.startsWith( "/" ) ? relativePath.substring( 1, relativePath.length() ) : relativePath;
               final String secondaryTargetPath = name + relativePath;
               add( nestedFile, target, secondaryTargetPath );
            }
            return;
         }

         String name = targetPath.replace( "\\", "/" );
         if( !name.endsWith( "/" ) ) {
            name += "/";
         }
         name += source.getName();
         if( !isManifestFile( name ) ) {
            final JarEntry entry = new JarEntry( name );
            entry.setTime( source.lastModified() );
            target.putNextEntry( entry );
            in = new BufferedInputStream( new FileInputStream( source ) );

            byte[] buffer = new byte[1024];
            while( true ) {
               int count = in.read( buffer );
               if( count == -1 ) {
                  break;
               }
               target.write( buffer, 0, count );
            }
            target.closeEntry();
         }
      }
      finally {
         if( in != null ) {
            in.close();
         }
      }
   }

   private File getManifestFileFromSources() {
      File result = null;
      for( Source source : sources ) {
         result = getManifestFromFileOrFolder( source.getSource() );
         if( result != null ) {
            break;
         }
      }
      return result;
   }

   private File getManifestFromFileOrFolder( File file ) {
      File result = null;
      if( file.isFile() ) {
         if( isManifestFile( file ) ) {
            result = file;
         }
      }
      else if( file.isDirectory() ) {
         for( File nestedFile : file.listFiles() ) {
            result = getManifestFromFileOrFolder( nestedFile );
            if( result != null ) {
               break;
            }
         }
      }
      return result;
   }

   private Manifest getManifestFromSources() {
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put( Attributes.Name.MANIFEST_VERSION, "1.0" );
      final File file = getManifestFileFromSources();
      if( file != null ) {
         try {
            manifest = new Manifest( new FileInputStream( file ) );
         }
         catch( Exception exception ) {
            throw new RuntimeException( "Error creating manifest from file: " + file, exception );
         }
      }
      return manifest;
   }

   private String getRelativePath( File dir, File file ) {
      final String path1 = dir.getAbsolutePath();
      final String path2 = file.getAbsolutePath();
      String result = path2;
      if( path2.startsWith( path1 ) ) {
         result = path2.substring( path1.length(), path2.length() );
         if( file.isFile() ) {
            result = result.substring( 0, result.length() - file.getName().length() );
         }
      }
      return result;
   }

   private boolean isManifestFile( File file ) {
      return isManifestFile( file.getAbsolutePath() );
   }

   private boolean isManifestFile( String path ) {
      return path.endsWith( "META-INF" + File.separator + "MANIFEST.MF" );
   }
}
