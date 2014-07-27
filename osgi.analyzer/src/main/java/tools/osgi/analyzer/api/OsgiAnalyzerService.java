package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings("deprecation")
public class OsgiAnalyzerService {

   BundleContext bundleContext;

   public OsgiAnalyzerService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   @Descriptor("Analyzes the state of the OSGi container")
   public void analyze(
         @Descriptor("Find all bundles with missing dependencies") @Parameter(names = { "-m", "--missing-dependencies" }, presentValue = "true", absentValue = "false") boolean initial )
   {
      printBundlesWithMissingDependencies();
   }

   private void printBundlesWithMissingDependencies() {
      final String format = "| %1$-35s|%2$10s |%3$25s |";
      final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
      System.out.println( line );
      System.out.println( String.format( format, "Bundle", "Bundle ID", "Missing Optional Imports" ) );
      System.out.println( line );
      for( Bundle bundle : findBundlesWithMissingDependencies() ) {
         final String bundleNameRaw = bundle.getSymbolicName();
         final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
         final Long bundleId = bundle.getBundleId();
         final int numOfMissingDependencies = getUnresolvedOptionalImportedPackages( bundle ).size();
         System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
      }
      System.out.println( line );
   }

   private List<Bundle> findBundlesWithMissingDependencies() {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( isRefreshRequired( bundle ) ) {
            result.add( bundle );
         }
      }
      return result;
   }

   private boolean isRefreshRequired( Bundle bundle ) {
      return getUnresolvedOptionalImportedPackages( bundle ).size() > 0;
   }

   private List<ImportedPackage> getOptionalImportedPackages( Bundle bundle ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         if( Resolution.OPTIONAL.equals( importedPackage.getResolution() ) ) {
            result.add( importedPackage );
         }
      }
      return result;
   }

   private List<ImportedPackage> getUnresolvedOptionalImportedPackages( Bundle bundle ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      for( ImportedPackage importedPackage : getOptionalImportedPackages( bundle ) ) {
         if( !isImportedPackageResolved( bundle, importedPackage ) ) {
            result.add( importedPackage );
         }
      }
      return result;
   }

   private boolean isImportedPackageResolved( Bundle bundle, ImportedPackage importedPackage ) {
      try {
         boolean result = false;
         final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
         final BundleWiring wiring = bundle.adapt( BundleWiring.class );
         for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
            final ExportedPackage[] exportedPackages = packageAdmin.getExportedPackages( required.getProviderWiring().getBundle() );
            for( ExportedPackage exportedPackage : exportedPackages ) {
               if( importedPackage.getPackageName().equals( exportedPackage.getName() ) ) {
                  result = true;
                  break;
               }
            }
            if( result ) {
               break;
            }
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed to determine import package resolved for bundle: %s Package: %s", bundle, importedPackage ), exception );
      }
   }

   private PackageAdmin getPackageAdmin( BundleContext context ) {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( context, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }

}
