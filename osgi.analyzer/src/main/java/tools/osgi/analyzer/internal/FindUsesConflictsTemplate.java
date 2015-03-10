package tools.osgi.analyzer.internal;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import tools.osgi.analyzer.api.BundleUtils;
import tools.osgi.analyzer.api.UsesConflict;
import tools.osgi.analyzer.internal.InternalOsgiAnalyzerService.BundleImportedPackage;

import com.springsource.util.osgi.VersionRange;
import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Template class that can be used to find use conflicts in an OSGi environment */
@SuppressWarnings({ "deprecation" })
public class FindUsesConflictsTemplate {
   public static class BundleFindUsesConflictsCallback implements IFindUsesConflictsCallback {
      private Bundle bundle;

      public BundleFindUsesConflictsCallback( Bundle bundle ) {
         this.bundle = bundle;
      }

      @Override
      public BundleManifest getManifest() {
         return BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      }

      @Override
      public boolean shouldCheck() {
         final PackageAdmin packageAdmin = getPackageAdmin();
         return !packageAdmin.resolveBundles( new Bundle[]{ bundle } );
      }

      private PackageAdmin getPackageAdmin() {
         final BundleContext bundleContext = bundle.getBundleContext();
         final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
         packageAdminTracker.open();
         final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
         return packageAdmin;
      }

      @Override
      public BundleWire getBundleWire( String packageName ) {
         return BundleUtils.getBundleWire( bundle.getBundleContext(), bundle, packageName );
      }
   }

   public static class BundleManifestFindUsesConflictsCallback implements IFindUsesConflictsCallback {
      private BundleManifest manifest;

      public BundleManifestFindUsesConflictsCallback( BundleManifest manifest ) {
         this.manifest = manifest;
      }

      @Override
      public BundleManifest getManifest() {
         return manifest;
      }

      @Override
      public boolean shouldCheck() {
         return true;
      }

      @Override
      public BundleWire getBundleWire( String packageName ) {
         return null;
      }
   }

   public static interface IFindUsesConflictsCallback {
      BundleManifest getManifest();

      boolean shouldCheck();

      /**
       * Get a {@link BundleWire} for the specified package if applicable and it exists
       * @param packageName Name of the package to find the existing wiring
       * @return {@link BundleWire} for the package if it exists
       */
      BundleWire getBundleWire( String packageName );
   }

   private BundleContext bundleContext;

   public FindUsesConflictsTemplate( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   public List<UsesConflict> find( IFindUsesConflictsCallback callback ) {
      return getUsesConflicts( callback );
   }

   public List<UsesConflict> find( IFindUsesConflictsCallback callback, ImportedPackage importedPackage ) {
      return getUsesConflicts( callback, importedPackage );
   }

   private List<UsesConflict> getHeaderUsesConflicts( IFindUsesConflictsCallback callback, Bundle usesConflictBundle, ImportedPackage use ) {
      final List<UsesConflict> result = new ArrayList<UsesConflict>();
      final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         if( VersionRange.intersection( match.getVersion(), use.getVersion() ).isEmpty() ) {
            result.add( new UsesConflict( bundleContext, callback.getManifest(), match, usesConflictBundle, use ) );
         }
      }
      return result;
   }

   private List<ImportedPackage> getImportedPackagesForExportUses( Bundle bundle, com.springsource.util.osgi.manifest.ExportedPackage exportedPackage ) {
      return getImportedPackagesForExportUses( bundle, exportedPackage, new ArrayList<String>() );
   }

   private List<ImportedPackage> getImportedPackagesForExportUses( Bundle bundle, com.springsource.util.osgi.manifest.ExportedPackage exportedPackage, List<String> exclude ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      if( exportedPackage != null ) {
         for( String use : exportedPackage.getUses() ) {
            if( !exclude.contains( use ) ) {
               final ImportedPackage importedPackage = BundleUtils.getImportedPackage( bundle, use );
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
      }
      return result;
   }

   private com.springsource.util.osgi.manifest.ExportedPackage getExportedPackage( Bundle bundle, String packageName ) {
      com.springsource.util.osgi.manifest.ExportedPackage result = null;
      for( com.springsource.util.osgi.manifest.ExportedPackage exportedPackage : BundleUtils.getExportedPackages( bundle ) ) {
         if( exportedPackage.getPackageName().equals( packageName ) ) {
            result = exportedPackage;
            break;
         }
      }
      return result;
   }

   private ImportedPackage getImportedPackageForBundleWire( Bundle bundle, BundleWire required ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : BundleUtils.getImportedPackages( bundle ) ) {
         final String filter = required.getRequirement().getDirectives().get( "filter" );
         final String packageFilter = String.format( "(osgi.wiring.package=%s)", importedPackage.getPackageName() );
         if( filter != null && filter.contains( packageFilter ) ) {
            result = importedPackage;
            break;
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

   private PackageAdmin getPackageAdmin() {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }

   private List<UsesConflict> getUsesConflicts( IFindUsesConflictsCallback callback ) {
      final List<UsesConflict> result = new ArrayList<UsesConflict>();
      if( callback.shouldCheck() ) {
         final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
         for( ImportedPackage importedPackage : importedPackages ) {
            result.addAll( getUsesConflicts( callback, importedPackage ) );
         }
      }
      return result;
   }

   /**
    * @param bundle Callback that provides bundle meta data to find use conflicts for
    * @param usesConflictBundleImport Bundle and Import Package down the dependency chain of the bundle being checked
    * @param alreadyChecked List of bundle/imports that have already been checked
    * @return List of use conflicts for the bundle combination
    */
   private List<UsesConflict> getUsesConflicts( IFindUsesConflictsCallback callback, BundleImportedPackage usesConflictBundleImport, List<BundleImportedPackage> alreadyChecked ) {
      final List<UsesConflict> result = new ArrayList<UsesConflict>();
      if( usesConflictBundleImport.getBundle() != null && usesConflictBundleImport.getImportedPackage() != null && !alreadyChecked.contains( usesConflictBundleImport ) ) {
         alreadyChecked.add( usesConflictBundleImport );

         final com.springsource.util.osgi.manifest.ExportedPackage exportedPackage = BundleUtils.getExportedPackage( usesConflictBundleImport.getBundle(), usesConflictBundleImport.getImportedPackage() );
         final List<ImportedPackage> uses = getImportedPackagesForExportUses( usesConflictBundleImport.getBundle(), exportedPackage );
         for( ImportedPackage use : uses ) {
            result.addAll( getHeaderUsesConflicts( callback, usesConflictBundleImport.getBundle(), use ) );
            result.addAll( getWiringUsesConflicts( callback, usesConflictBundleImport.getBundle(), use ) );
         }

         // Secondary Dependencies
         final BundleWiring wiring = usesConflictBundleImport.getBundle().adapt( BundleWiring.class );
         if( wiring != null ) {
            for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
               if( required.getProviderWiring() != null ) {
                  final Bundle secondaryUsesConflictBundle = required.getProviderWiring().getBundle();
                  final ImportedPackage secondaryImportedPackage = getImportedPackageForBundleWire( usesConflictBundleImport.getBundle(), required );
                  final BundleImportedPackage secondaryBundleImport = new BundleImportedPackage( secondaryUsesConflictBundle, secondaryImportedPackage );
                  result.addAll( getUsesConflicts( callback, secondaryBundleImport, alreadyChecked ) );
               }
            }
         }
      }
      return result;
   }

   /**
    * @param callback Callback that provides bundle meta data to find use conflicts for
    * @param importedPackage Import Package from the bundle to check for conflicts down the dependency tree
    * @return List of all Use Conflicts found recursively
    */
   private List<UsesConflict> getUsesConflicts( IFindUsesConflictsCallback callback, ImportedPackage importedPackage ) {
      final List<UsesConflict> result = new ArrayList<UsesConflict>();
      final Bundle match = BundleUtils.findBestMatchThatSatisfiesImport( bundleContext, importedPackage );
      if( match != null ) {
         result.addAll( getUsesConflicts( callback, new BundleImportedPackage( match, importedPackage ), new ArrayList<BundleImportedPackage>() ) );
      }
      return result;
   }

   private List<UsesConflict> getWiringUsesConflicts( IFindUsesConflictsCallback callback, Bundle usesConflictBundle, ImportedPackage use ) {
      return getWiringUsesConflicts( callback, usesConflictBundle, use, new ArrayList<Bundle>() );
   }

   /**
    * @param bundle Callback that provides bundle meta data to find use conflicts for
    * @param usesConflictBundle Bundle that satisfies an import of the bundle that will not start
    * @param use Use Import that could be causing the use conflict
    * @param alreadyChecked List of bundles that have already been checked
    * @return All Use Conflicts found with the wiring of the potential use conflict bundle
    */
   private List<UsesConflict> getWiringUsesConflicts( IFindUsesConflictsCallback callback, Bundle usesConflictBundle, ImportedPackage use, List<Bundle> alreadyChecked ) {
      final List<UsesConflict> result = new ArrayList<UsesConflict>();
      if( !alreadyChecked.contains( usesConflictBundle ) ) {
         alreadyChecked.add( usesConflictBundle );
         final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
         final ImportedPackage match = getMatchingImport( importedPackages, use );
         if( match != null ) {
            final PackageAdmin packageAdmin = getPackageAdmin();
            if( packageAdmin.resolveBundles( new Bundle[]{ usesConflictBundle } ) ) {
               if( !BundleUtils.isImportedPackageResolved( bundleContext, usesConflictBundle, use ) ) {
                  // ADD CONFLICT IF OPTIONAL PACKAGE NOT RESOLVED?
               }
               final BundleWire usesConflictWire = BundleUtils.getBundleWire( bundleContext, usesConflictBundle, use.getPackageName() );
               if( usesConflictWire != null ) {
                  final Bundle usesConflictProvidingBundle = usesConflictWire.getProviderWiring().getBundle();
                  if( !BundleUtils.containsExportForImport( usesConflictProvidingBundle, match ) ) {
                     final com.springsource.util.osgi.manifest.ExportedPackage usesConflictExportPackage = getExportedPackage( usesConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                     result.add( new UsesConflict( bundleContext, callback.getManifest(), match, usesConflictBundle, usesConflictExportPackage ) );
                  }
                  else {
                     // Multiple Matches
                     final List<Bundle> allMatchingBundles = BundleUtils.findBundlesThatSatisfyImport( bundleContext, match );
                     if( allMatchingBundles.size() > 1 ) {
                        System.out.println( "Multiple bundles could match this import causing the uses conflict?" );
                        System.out.println( allMatchingBundles );
                     }

                     // Crossed Wires?
                     final BundleWire wiring = callback.getBundleWire( match.getPackageName() );
                     if( wiring != null ) {
                        if( !wiring.getProviderWiring().getBundle().equals( usesConflictWire.getProviderWiring().getBundle() ) ) {
                           final com.springsource.util.osgi.manifest.ExportedPackage usesConflictExportPackage = getExportedPackage( usesConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                           result.add( new UsesConflict( bundleContext, callback.getManifest(), match, usesConflictBundle, usesConflictExportPackage ) );
                        }
                     }
                  }
               }
            }
         }
         else {
            final BundleWiring wiring = usesConflictBundle.adapt( BundleWiring.class );
            if( wiring != null ) {
               for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
                  if( required.getProviderWiring() != null ) {
                     final Bundle secondaryUsesConflictBundle = required.getProviderWiring().getBundle();
                     result.addAll( getWiringUsesConflicts( callback, secondaryUsesConflictBundle, use, alreadyChecked ) );
                  }
               }
            }
         }
      }
      return result;
   }
}
