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
               absentValue = "false") boolean includeMissingDependencies,
         @Descriptor("Find all bundles with use conflicts") @Parameter(
               names = { "-u", "--use-conflicts" },
               presentValue = "true",
               absentValue = "false") boolean includeUseConflicts,
         @Descriptor("Find all issues with bundles") @Parameter(
               names = { "-a", "--all" },
               presentValue = "true",
               absentValue = "false") boolean includeAll
         ) {
      if( includeMissingDependencies || includeAll ) {
         printBundlesWithMissingDependencies();
      }
      if( includeUseConflicts || includeAll ) {
         printBundlesWithUseConflicts();
      }
   }

   private boolean containsExportForImport( Bundle bundle, ImportedPackage importedPackage ) {
      return getExportedPackage( bundle, importedPackage ) != null;
   }

   @Descriptor("Diagnoses issues with a specified bundle")
   public void diagnose(
         @Descriptor("Bundle ID to diagnose issues") String bundleId
         ) {
      // Get Bundle
      final Bundle bundle = bundleContext.getBundle( Long.valueOf( bundleId ) );
      if( bundle == null ) {
         throw new IllegalArgumentException( String.format( "No bundle could be found for %s", bundleId ) );
      }

      // Print
      System.out.println( "Bundle: " + bundle );
      printUnresolvedImports( bundle );
      printUseConflicts( bundle );
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

   private BundleWire getBundleWire( Bundle bundle, String packageName ) {
      try {
         BundleWire result = null;
         final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
         final BundleWiring wiring = bundle.adapt( BundleWiring.class );
         for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
            final ExportedPackage[] exportedPackages = packageAdmin.getExportedPackages( required.getProviderWiring().getBundle() );
            for( ExportedPackage exportedPackage : exportedPackages ) {
               if( packageName.equals( exportedPackage.getName() ) ) {
                  result = required;
                  break;
               }
            }
            if( result != null ) {
               break;
            }
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed to determine bundle wiring for bundle: %s Package: %s", bundle, packageName ), exception );
      }
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

   private com.springsource.util.osgi.manifest.ExportedPackage getExportedPackage( Bundle bundle, String packageName ) {
      com.springsource.util.osgi.manifest.ExportedPackage result = null;
      for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : getExportedPackages( bundle ) ) {
         if( exportedPackage.getPackageName().equals( packageName ) ) {
            result = exportedPackage;
            break;
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

   private List<UseConflict> getHeaderUseConflicts( Bundle bundle, Bundle useConflictBundle, ImportedPackage use ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         if( VersionRange.intersection( match.getVersion(), use.getVersion() ).isEmpty() ) {
            result.add( new UseConflict( bundle, match, useConflictBundle, use ) );
         }
      }
      return result;
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

   @SuppressWarnings("unused")
   private Comparator<ImportedPackage> getImportedPackageComparator() {
      return new Comparator<ImportedPackage>() {
         @Override
         public int compare( ImportedPackage p1, ImportedPackage p2 ) {
            return p1.getPackageName().compareTo( p2.getPackageName() );
         }
      };
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

   private List<ImportedPackage> getImportedPackagesForExportUses( Bundle bundle, com.springsource.util.osgi.manifest.ExportedPackage exportedPackage ) {
      return getImportedPackagesForExportUses( bundle, exportedPackage, new ArrayList<String>() );
   }

   private List<ImportedPackage> getImportedPackagesForExportUses( Bundle bundle, com.springsource.util.osgi.manifest.ExportedPackage exportedPackage, List<String> exclude ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      for( String use : exportedPackage.getUses() ) {
         if( !exclude.contains( use ) ) {
            final ImportedPackage importedPackage = getImportedPackage( bundle, use );
            if( importedPackage != null ) {
               result.add( importedPackage );
               exclude.add( use );
            }
            else {
               // Cascade Uses
               final com.springsource.util.osgi.manifest.ExportedPackage cascadeExportedPackage = getExportedPackage( bundle, use );
               if( cascadeExportedPackage != null ) {
                  exclude.add( use );
                  result.addAll( getImportedPackagesForExportUses( bundle, cascadeExportedPackage, exclude ) );
               }
            }
         }
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

   private List<UseConflict> getUseConflicts( Bundle bundle ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
      if( !packageAdmin.resolveBundles( new Bundle[]{ bundle } ) ) {
         final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
         for( ImportedPackage importedPackage : importedPackages ) {
            final Bundle match = findBestMatchThatSatisfiesImport( importedPackage );
            if( match != null ) {
               final com.springsource.util.osgi.manifest.ExportedPackage exportedPackage = getExportedPackage( match, importedPackage );
               final List<ImportedPackage> uses = getImportedPackagesForExportUses( match, exportedPackage );
               for( ImportedPackage use : uses ) {
                  result.addAll( getHeaderUseConflicts( bundle, match, use ) );
                  result.addAll( getWiringUseConflicts( bundle, match, use ) );
               }
            }
         }
      }
      return result;
   }

   private List<UseConflict> getWiringUseConflicts( Bundle bundle, Bundle useConflictBundle, ImportedPackage use ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
         if( packageAdmin.resolveBundles( new Bundle[]{ useConflictBundle } ) ) {
            if( !isImportedPackageResolved( useConflictBundle, use ) ) {
               // ADD CONFLICT IF OPTIONAL PACKAGE NOT RESOLVED?
            }
            final BundleWire wiring = getBundleWire( useConflictBundle, use.getPackageName() );
            if( wiring != null ) {
               if( !containsExportForImport( wiring.getProviderWiring().getBundle(), match ) ) {
                  final com.springsource.util.osgi.manifest.ExportedPackage useConflictExportPackage = getExportedPackage( wiring.getProviderWiring().getBundle(), match.getPackageName() );
                  result.add( new UseConflict( bundle, match, useConflictBundle, useConflictExportPackage ) );
               }
            }
         }
      }
      return result;
   }

   private boolean hasUseConflict( Bundle bundle ) {
      return getUseConflicts( bundle ).size() > 0;
   }

   private boolean isImportedPackageResolved( Bundle bundle, ImportedPackage importedPackage ) {
      return getBundleWire( bundle, importedPackage.getPackageName() ) != null;
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
         final int numOfMissingDependencies = getUseConflicts( bundle ).size();
         System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
      }
      System.out.println( line );
   }

   private void printUnresolvedImports( Bundle bundle ) {
      final List<ImportedPackage> unresolvedImports = getUnresolvedOptionalImportedPackages( bundle );
      if( unresolvedImports.size() > 0 ) {
         final String format = "| %1$-35s| %2$-35s|";
         final String line = new String( new char[String.format( format, "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Unresolved Import", "Version" ) );
         System.out.println( line );
         for( ImportedPackage importedPackage : unresolvedImports ) {
            System.out.println( String.format( format, importedPackage.getPackageName(), importedPackage.getVersion().toString() ) );
         }
         System.out.println( line );
      }
   }

   private void printUseConflicts( Bundle bundle ) {
      final List<UseConflict> useConflicts = getUseConflicts( bundle );
      if( useConflicts.size() > 0 ) {
         final String format = "| %1$-10s| %2$-45s| %3$-35s| %4$-28s| %5$-28s|";
         final String line = new String( new char[String.format( format, "", "", "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Type", "Import", "Conflict Bundle", "Import Version", "Conflict Version" ) );
         System.out.println( line );
         for( UseConflict useConflict : useConflicts ) {
            final String type = useConflict.getType().name();
            final String importPackageName = useConflict.getImportedPackage().getPackageName();
            final String conflictBundle = useConflict.getUseConflictBundle().getSymbolicName();
            final String importVersion = useConflict.getImportedPackage().getVersion().toString();
            final String conflictVersion = UseConflictType.Header.equals( useConflict.getType() ) ? useConflict.getUseConflictImportedPackage().getVersion().toParseString() : useConflict.getUseConflictExportedPackage().getVersion().toString();
            System.out.println( String.format( format, type, importPackageName, conflictBundle, importVersion, conflictVersion ) );
         }
         System.out.println( line );
      }
   }

}
