package osgi.analyzer.test;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Create differences between two manifest files */
public class TestManifestCompare {

   @Test
   public void compare1() throws Exception {
      // Fixture
      BundleManifest manifest1 = BundleManifestFactory.createBundleManifest( new InputStreamReader( getClass().getResourceAsStream( "/manifest1.mf" ) ), new DummyParserLogger() );
      BundleManifest manifest2 = BundleManifestFactory.createBundleManifest( new InputStreamReader( getClass().getResourceAsStream( "/manifest2.mf" ) ), new DummyParserLogger() );

      // Call
      for( ImportedPackage importedPackage : manifest1.getImportPackage().getImportedPackages() ) {
         if( !isImportPackage( manifest2, importedPackage.getPackageName() ) ) {
            System.out.println( "Removed Import: " + importedPackage );
         }
         else if( !importedPackage.equals( getImportPackage( manifest2, importedPackage.getPackageName() ) ) ) {
            System.out.println( "Import Diff: " + importedPackage + " | " + getImportPackage( manifest2, importedPackage.getPackageName() ) );
         }
      }
      for( ImportedPackage importedPackage : manifest2.getImportPackage().getImportedPackages() ) {
         if( !isImportPackage( manifest1, importedPackage.getPackageName() ) ) {
            System.out.println( "Added Import: " + importedPackage );
         }
      }

      final List<String> importPackagesFound = new ArrayList<String>();
      for( ImportedPackage importedPackage : manifest2.getImportPackage().getImportedPackages() ) {
         if( importPackagesFound.contains( importedPackage.getPackageName() ) ) {
            System.out.println( "Duplicate Import: " + importedPackage.getPackageName() );
         }
         importPackagesFound.add( importedPackage.getPackageName() );
      }
   }

   private boolean isImportPackage( BundleManifest manifest, String packageName ) {
      return getImportPackage( manifest, packageName ) != null;
   }

   private ImportedPackage getImportPackage( BundleManifest manifest, String packageName ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         if( importedPackage.getPackageName().equals( packageName ) ) {
            result = importedPackage;
            break;
         }
      }
      return result;
   }
}
