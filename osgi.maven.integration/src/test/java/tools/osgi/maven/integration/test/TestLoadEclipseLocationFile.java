package tools.osgi.maven.integration.test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Validate;
import org.junit.Test;

public class TestLoadEclipseLocationFile {

   //   @Test
   public void testPrintProjectLocations() throws Exception {
      // Fixture
      final String workspacePath = "/home/developer/Workspaces/Workspace - Yaas3";

      // Call
      final Map<String, URI> projects = new HashMap<String, URI>();
      final File workspaceFolder = new File( workspacePath );
      Validate.isTrue( workspaceFolder.exists(), "Workspace: %s does not exist", workspacePath );
      Validate.isTrue( workspaceFolder.isDirectory(), "Workspace: %s is not a directory", workspacePath );
      final File projectsFolder = new File( workspaceFolder.getAbsolutePath() + File.separator + ".metadata" + File.separator + ".plugins" + File.separator + "org.eclipse.core.resources" + File.separator + ".projects" );
      if( projectsFolder.exists() && projectsFolder.isDirectory() ) {
         for( File child : projectsFolder.listFiles() ) {
            if( child.isDirectory() ) {
               final String projectName = child.getName();
               final File locationFile = new File( child.getAbsolutePath() + File.separator + ".location" );
               if( locationFile.exists() ) {
                  if( getUriFromEclipseLocationFile( locationFile ) != null ) {
                     projects.put( projectName, getUriFromEclipseLocationFile( locationFile ) );
                  }
               }
            }
         }
      }

      // Assert
      System.out.println( projects );
   }

   // WORKING!!!
   @Test
   public void testLoadFile() throws Exception {
      // Fixture
      final InputStream is = new DataInputStream(
            new BufferedInputStream( getClass().getResourceAsStream( "/eclipse-location-file" ) ) );
      // Eclipse Workspace: /[ECLIPSE_WORKSPACE]/.metadata/.plugins/org.eclipse.core.resources/.projects
      // Each folder represents a project in the work space
      // Within each folder is a .location file that points to the location of the project

      // http://www.joeflash.ca/blog/2008/11/moving-a-fb-workspace-update.html

      // Call
      //      final ObjectInputStream ois = new ObjectInputStream( is );
      //      ois.readObject();

      // SerializationUtils.deserialize( IOUtils.toByteArray( is ) );

      // final char[] chars = Hex.encodeHex( IOUtils.toByteArray( is ) );

      // System.out.println( chars );

      // final StringBuffer txtBuffer = new StringBuffer();
      // final byte[] bytes = IOUtils.toByteArray( is );

      //      for( byte b : bytes ) {
      //         hexBuffer.append( HexStringConverter.getHexStringConverterInstance().hexToString( txtInHex ) ).append( " " );
      //      }

      //      
      //         final String txt = toAscii( hex );
      //         
      //         txtBuffer.append( txt + " " );

      //      int read;
      //      while( ( read = is.read() ) != -1 ) {
      //         // System.out.print( Integer.toHexString( read ) + "\t" );
      //         final String hex = Integer.toHexString( read );
      //         hexBuffer.append( hex );
      //         txtBuffer.append( String.valueOf( ( char )read ) );
      //      }

      final String hex = toHex( is );
      final String txt = toAscii( hex );
      final URI url = getUri( txt );
      System.out.println( "HEX: " + hex );
      System.out.println( "TXT: " + txt );
      System.out.println( "URL: " + url );

      //      int read;
      //      while( ( read = is.read() ) != -1 ) {
      //         System.out.print( Integer.toBinaryString( read ) + "\t" );
      //      }

   }

   public URI getUriFromEclipseLocationFile( File locationFile ) {
      final String hex = toHex( locationFile );
      final String txt = toAscii( hex );
      return getUri( txt );
   }

   public URI getUri( String txt ) {
      final String uriText = getUriAsString( txt );
      Validate.notEmpty( uriText, "No URI could be found in the text" );
      try {
         return new URI( uriText );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error creating URI for: %s", uriText ), exception );
      }
   }

   private String getUriAsString( String txt ) {
      final StringBuffer result = new StringBuffer();
      final String search = "URI//";
      if( txt.indexOf( search ) >= 0 ) {
         int index = txt.indexOf( search ) + search.length();
         for( ; index < txt.length(); index++ ) {
            final String character = String.valueOf( txt.charAt( index ) );
            if( isAsciiPrintable( character ) ) {
               result.append( character );
            }
            else {
               break;
            }
         }
      }
      return result.toString();
   }

   private String toHex( File file ) {
      try {
         return toHex( new FileInputStream( file ) );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting file: %s as HEX", file ), exception );
      }
   }

   private String toHex( InputStream stream ) {
      try {
         final InputStream is = new DataInputStream( new BufferedInputStream( stream ) );
         final StringBuffer hexBuffer = new StringBuffer();
         int read;
         while( ( read = is.read() ) != -1 ) {
            final String hex = Integer.toHexString( read );
            hexBuffer.append( hex );
         }
         return hexBuffer.toString();
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Failed to convert stream to HEX", exception );
      }
   }

   private String toAscii( String hex ) {
      final StringBuilder sb = new StringBuilder();
      for( int i = 0; i < hex.length() - 1; i += 2 ) {
         final String output = hex.substring( i, ( i + 2 ) );
         final int decimal = Integer.parseInt( output, 16 );
         sb.append( ( char )decimal );
      }
      return sb.toString();
   }

   /**
    * <p>Checks if the string contains only ASCII printable characters.</p>
    * 
    * <p><code>null</code> will return <code>false</code>.
    * An empty String ("") will return <code>true</code>.</p>
    * 
    * <pre>
    * StringUtils.isAsciiPrintable(null)     = false
    * StringUtils.isAsciiPrintable("")       = true
    * StringUtils.isAsciiPrintable(" ")      = true
    * StringUtils.isAsciiPrintable("Ceki")   = true
    * StringUtils.isAsciiPrintable("ab2c")   = true
    * StringUtils.isAsciiPrintable("!ab-c~") = true
    * StringUtils.isAsciiPrintable("\u0020") = true
    * StringUtils.isAsciiPrintable("\u0021") = true
    * StringUtils.isAsciiPrintable("\u007e") = true
    * StringUtils.isAsciiPrintable("\u007f") = false
    * StringUtils.isAsciiPrintable("Ceki G\u00fclc\u00fc") = false
    * </pre>
    *
    * @param str the string to check, may be null
    * @return <code>true</code> if every character is in the range
    *  32 thru 126
    * @since 2.1
    */
   public static boolean isAsciiPrintable( String str ) {
      if( str == null ) {
         return false;
      }
      int sz = str.length();
      for( int i = 0; i < sz; i++ ) {
         if( isAsciiPrintable( str.charAt( i ) ) == false ) {
            return false;
         }
      }
      return true;
   }

   /**
    * <p>Checks whether the character is ASCII 7 bit printable.</p>
    *
    * <pre>
    *   CharUtils.isAsciiPrintable('a')  = true
    *   CharUtils.isAsciiPrintable('A')  = true
    *   CharUtils.isAsciiPrintable('3')  = true
    *   CharUtils.isAsciiPrintable('-')  = true
    *   CharUtils.isAsciiPrintable('\n') = false
    *   CharUtils.isAsciiPrintable('&copy;') = false
    * </pre>
    * 
    * @param ch  the character to check
    * @return true if between 32 and 126 inclusive
    */
   public static boolean isAsciiPrintable( char ch ) {
      return ch >= 32 && ch < 127;
   }
}
