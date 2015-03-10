package tools.osgi.maven.integration.test;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import tools.osgi.maven.integration.internal.UsesConflictError;

public class TestParseUsesConflictExceptionMessage {

   @Test
   public void testMessage1() throws Exception {
      // Fixture
      final String message = IOUtils.toString( getClass().getResourceAsStream( "/virgo-uses-conflict-exception-message1.txt" ) );

      // Call
      final List<UsesConflictError> conflicts = parseConflicts( message );

      // Assert
      System.out.println( conflicts );
   }

   private List<UsesConflictError> parseConflicts( String message ) {
      final List<UsesConflictError> result = new ArrayList<UsesConflictError>();
      final String[] array = message.split( "Uses violation:" );
      for( int index = 1; index < array.length; index += 2 ) {
         final String sub = array[index];
         final String searchText = "<Import-Package:";
         final int startIndex = sub.indexOf( searchText ) + searchText.length();
         final int endIndex = sub.indexOf( ";" );
         final String packageName = sub.substring( startIndex, endIndex ).trim();
         final int startIndex2 = sub.indexOf( "\"" ) + 1;
         final int endIndex2 = sub.indexOf( "\"", startIndex2 );
         final String version = sub.substring( startIndex2, endIndex2 ).trim();
         final String searchText2 = "bundle <";
         final int startIndex3 = sub.indexOf( searchText2 ) + searchText2.length();
         final int endIndex3 = sub.indexOf( "_", startIndex3 );
         final String bundleSymbolicName = sub.substring( startIndex3, endIndex3 ).trim();
         result.add( new UsesConflictError( packageName, version, bundleSymbolicName ) );
      }

      return result;
   }
}
