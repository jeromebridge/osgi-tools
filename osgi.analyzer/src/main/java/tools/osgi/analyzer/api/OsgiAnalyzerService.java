package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.Comparator;
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

import com.springsource.util.osgi.VersionRange;
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

   private boolean containsExportForImport( Bundle bundle, ImportedPackage importedPackage ) {
      return getExportedPackage( bundle, importedPackage ) != null;
   }

   private Bundle findBestMatchThatSatisfiesImport( ImportedPackage importedPackage ) {
      final List<Bundle> matches = findBundlesThatSatisfyImport( importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   private List<Bundle> findBundlesThatSatisfyImport( ImportedPackage importedPackage ) {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( containsExportForImport( bundle, importedPackage ) ) {
            result.add( bundle );
         }
      }
      return result;
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

   private com.springsource.util.osgi.manifest.ExportedPackage getExportedPackage( Bundle bundle, ImportedPackage importedPackage ) {
      com.springsource.util.osgi.manifest.ExportedPackage result = null;
      final List<com.springsource.util.osgi.manifest.ExportedPackage> exportedPackages = getExportedPackages( bundle );
      for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : exportedPackages ) {
         if( exportedPackage.getPackageName().equals( importedPackage.getPackageName() ) ) {
            if( importedPackage.getVersion().includes( exportedPackage.getVersion() ) ) {
               result = exportedPackage;
               break;
            }
         }
      }
      return result;
   }

   private List<com.springsource.util.osgi.manifest.ExportedPackage> getExportedPackages( Bundle bundle ) {
      final List<com.springsource.util.osgi.manifest.ExportedPackage> result = new ArrayList<com.springsource.util.osgi.manifest.ExportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : manifest.getExportPackage().getExportedPackages() ) {
         result.add( exportedPackage );
      }
      return result;
   }

   private List<ImportedPackage> getImportedPackages( Bundle bundle ) {
      return getImportedPackages( bundle, null );
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

   private boolean hasUseConflict( Bundle bundle ) {
      boolean result = false;
      final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
      if( !packageAdmin.resolveBundles( new Bundle[]{ bundle } ) ) {
         // Header Conflicts
         final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
         for( ImportedPackage importedPackage : importedPackages ) {
            final Bundle match = findBestMatchThatSatisfiesImport( importedPackage );
            if( match != null ) {
               final com.springsource.util.osgi.manifest.ExportedPackage exportedPackage = getExportedPackage( match, importedPackage );
               final List<ImportedPackage> uses = getImportedPackagesForExportUses( match, exportedPackage );
               for( ImportedPackage use : uses ) {
                  result = result || containsHeaderConflict( importedPackages, use );
                  result = result || containsWiringConflict( match, importedPackages, use );
               }
            }
            if( result ) {
               break;
            }
         }
      }
      return result;
   }

   private boolean containsWiringConflict( Bundle providingBundle, List<ImportedPackage> importedPackages, ImportedPackage use ) {
      boolean result = false;
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
         if( packageAdmin.resolveBundles( new Bundle[]{ providingBundle } ) ) {
            result = isImportedPackageResolved( providingBundle, use );
         }
      }
      return result;
   }

   private boolean containsHeaderConflict( List<ImportedPackage> importedPackages, ImportedPackage use ) {
      boolean result = false;
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         result = VersionRange.intersection( match.getVersion(), use.getVersion() ).isEmpty();
      }
      return result;
   }

   private ImportedPackage getMatchingImport( List<ImportedPackage> importedPackages, ImportedPackage use ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : importedPackages ) {
         if( importedPackage.getPackageName().equals( use.getPackageName() ) ) {
            result = importedPackage;
         }
      }
      return result;
   }

   private List<ImportedPackage> getImportedPackagesForExportUses( Bundle bundle, com.springsource.util.osgi.manifest.ExportedPackage exportedPackage ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      for( String use : exportedPackage.getUses() ) {
         final ImportedPackage importedPackage = getImportedPackage( bundle, use );
         if( importedPackage != null ) {
            result.add( importedPackage );
         }
      }
      return result;
   }

   @SuppressWarnings("unused")
   private Comparator<ImportedPackage> getImportedPackageComparator() {
      return new Comparator<ImportedPackage>() {
         @Override
         public int compare( ImportedPackage p1, ImportedPackage p2 ) {
            return p1.getPackageName().compareTo( p2.getPackageName() );
         }
      };
   }

   private ImportedPackage getImportedPackage( Bundle bundle, String packageName ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : getImportedPackages( bundle ) ) {
         if( importedPackage.getPackageName().equals( packageName ) ) {
            result = importedPackage;
            break;
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
