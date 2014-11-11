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
         @Descriptor("Find all bundles with missing dependencies") @Parameter(
               names = { "-m", "--missing-dependencies" },
               presentValue = "true",
               absentValue = "true") boolean includeMissingDependencies
         )
   {
      if( includeMissingDependencies ) {
         printBundlesWithMissingDependencies();
      }
      printBundlesWithUseConflicts();
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

   private List<Bundle> findBundlesWithUseConflicts() {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( hasUseConflict( bundle ) ) {
            result.add( bundle );
         }
      }
      return result;
   }

   private boolean hasUseConflict( Bundle bundle ) {
      boolean result = false;
      final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
      if( !packageAdmin.resolveBundles( new Bundle[]{ bundle } ) ) {
         result = true;
         for( ImportedPackage importedPackage : getImportedPackages( bundle ) ) {
            final List<Bundle> matches = findBundlesThatSatisfyImport( importedPackage );

            System.out.println( "Import: " + importedPackage );
            System.out.println( "Matches: " + matches );

            //            final ExportedPackage[] exportedPackages = packageAdmin.getExportedPackages( required.getProviderWiring().getBundle() );
            //            for( ExportedPackage exportedPackage : exportedPackages ) {
            //               if( importedPackage.getPackageName().equals( exportedPackage.getName() ) ) {
            //                  result = true;
            //                  break;
            //               }
            //            }
            //            if( result ) {
            //               break;
            //            }
         }

      }
      return result;
   }

   private List<Bundle> findBundlesThatSatisfyImport( ImportedPackage importedPackage ) {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         final List<com.springsource.util.osgi.manifest.ExportedPackage> exportedPackages = getExportedPackages( bundle );
         for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : exportedPackages ) {
            if( exportedPackage.getPackageName().equals( importedPackage.getPackageName() ) ) {
               if( importedPackage.getVersion().includes( exportedPackage.getVersion() ) ) {
                  result.add( bundle );
                  System.out.println( "Export " + exportedPackage.getPackageName() + " Uses: " + exportedPackage.getUses() );
                  break;
               }
            }
         }
      }
      return result;
   }

   private List<ImportedPackage> getImportedPackages( Bundle bundle ) {
      return getImportedPackages( bundle, null );
   }

   private List<com.springsource.util.osgi.manifest.ExportedPackage> getExportedPackages( Bundle bundle ) {
      final List<com.springsource.util.osgi.manifest.ExportedPackage> result = new ArrayList<com.springsource.util.osgi.manifest.ExportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : manifest.getExportPackage().getExportedPackages() ) {
         result.add( exportedPackage );
      }
      return result;
   }

   private List<ImportedPackage> getImportedPackages( Bundle bundle, Resolution resolution ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         if( resolution == null || resolution.equals( importedPackage.getResolution() ) ) {
            result.add( importedPackage );
         }
      }
      return result;
   }

   private List<ImportedPackage> getOptionalImportedPackages( Bundle bundle ) {
      return getImportedPackages( bundle, Resolution.OPTIONAL );
   }

   private PackageAdmin getPackageAdmin( BundleContext context ) {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( context, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
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

   private boolean isRefreshRequired( Bundle bundle ) {
      return getUnresolvedOptionalImportedPackages( bundle ).size() > 0;
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

   private void printBundlesWithUseConflicts() {
      final String format = "| %1$-35s|%2$10s |%3$25s |";
      final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
      System.out.println( line );
      System.out.println( String.format( format, "Bundle", "Bundle ID", "Use Conflicts" ) );
      System.out.println( line );
      for( Bundle bundle : findBundlesWithUseConflicts() ) {
         final String bundleNameRaw = bundle.getSymbolicName();
         final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
         final Long bundleId = bundle.getBundleId();
         final int numOfMissingDependencies = getUnresolvedOptionalImportedPackages( bundle ).size();
         System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
      }
      System.out.println( line );
   }

}
