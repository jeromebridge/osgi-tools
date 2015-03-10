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
import tools.osgi.analyzer.api.UseConflict;
import tools.osgi.analyzer.internal.InternalOsgiAnalyzerService.BundleImportedPackage;

import com.springsource.util.osgi.VersionRange;
import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Template class that can be used to find use conflicts in an OSGi environment */
@SuppressWarnings({ "deprecation" })
public class FindUseConflictsTemplate {
   public static class BundleFindUseConflictsCallback implements IFindUseConflictsCallback {
      private Bundle bundle;

      public BundleFindUseConflictsCallback( Bundle bundle ) {
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

   public static class BundleManifestFindUseConflictsCallback implements IFindUseConflictsCallback {
      private BundleManifest manifest;

      public BundleManifestFindUseConflictsCallback( BundleManifest manifest ) {
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

   public static interface IFindUseConflictsCallback {
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

   public FindUseConflictsTemplate( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   public List<UseConflict> find( IFindUseConflictsCallback callback ) {
      return getUseConflicts( callback );
   }

   public List<UseConflict> find( IFindUseConflictsCallback callback, ImportedPackage importedPackage ) {
      return getUseConflicts( callback, importedPackage );
   }

   private List<UseConflict> getHeaderUseConflicts( IFindUseConflictsCallback callback, Bundle useConflictBundle, ImportedPackage use ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         if( VersionRange.intersection( match.getVersion(), use.getVersion() ).isEmpty() ) {
            result.add( new UseConflict( bundleContext, callback.getManifest(), match, useConflictBundle, use ) );
         }
      }
      return result;
   }

   private ImportedPackage getImportedPackage( Bundle bundle, String packageName ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : BundleUtils.getImportedPackages( bundle ) ) {
         if( importedPackage.getPackageName().equals( packageName ) ) {
            result = importedPackage;
            break;
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

   private List<UseConflict> getUseConflicts( IFindUseConflictsCallback callback ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      if( callback.shouldCheck() ) {
         final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
         for( ImportedPackage importedPackage : importedPackages ) {
            result.addAll( getUseConflicts( callback, importedPackage ) );
         }
      }
      return result;
   }

   /**
    * @param bundle Callback that provides bundle meta data to find use conflicts for
    * @param useConflictBundleImport Bundle and Import Package down the dependency chain of the bundle being checked
    * @param alreadyChecked List of bundle/imports that have already been checked
    * @return List of use conflicts for the bundle combination
    */
   private List<UseConflict> getUseConflicts( IFindUseConflictsCallback callback, BundleImportedPackage useConflictBundleImport, List<BundleImportedPackage> alreadyChecked ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      if( useConflictBundleImport.getBundle() != null && useConflictBundleImport.getImportedPackage() != null && !alreadyChecked.contains( useConflictBundleImport ) ) {
         alreadyChecked.add( useConflictBundleImport );

         final com.springsource.util.osgi.manifest.ExportedPackage exportedPackage = BundleUtils.getExportedPackage( useConflictBundleImport.getBundle(), useConflictBundleImport.getImportedPackage() );
         final List<ImportedPackage> uses = getImportedPackagesForExportUses( useConflictBundleImport.getBundle(), exportedPackage );
         for( ImportedPackage use : uses ) {
            result.addAll( getHeaderUseConflicts( callback, useConflictBundleImport.getBundle(), use ) );
            result.addAll( getWiringUseConflicts( callback, useConflictBundleImport.getBundle(), use ) );
         }

         // Secondary Dependencies
         final BundleWiring wiring = useConflictBundleImport.getBundle().adapt( BundleWiring.class );
         if( wiring != null ) {
            for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
               if( required.getProviderWiring() != null ) {
                  final Bundle secondaryUseConflictBundle = required.getProviderWiring().getBundle();
                  final ImportedPackage secondaryImportedPackage = getImportedPackageForBundleWire( useConflictBundleImport.getBundle(), required );
                  final BundleImportedPackage secondaryBundleImport = new BundleImportedPackage( secondaryUseConflictBundle, secondaryImportedPackage );
                  result.addAll( getUseConflicts( callback, secondaryBundleImport, alreadyChecked ) );
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
   private List<UseConflict> getUseConflicts( IFindUseConflictsCallback callback, ImportedPackage importedPackage ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final Bundle match = BundleUtils.findBestMatchThatSatisfiesImport( bundleContext, importedPackage );
      if( match != null ) {
         result.addAll( getUseConflicts( callback, new BundleImportedPackage( match, importedPackage ), new ArrayList<BundleImportedPackage>() ) );
      }
      return result;
   }

   private List<UseConflict> getWiringUseConflicts( IFindUseConflictsCallback callback, Bundle useConflictBundle, ImportedPackage use ) {
      return getWiringUseConflicts( callback, useConflictBundle, use, new ArrayList<Bundle>() );
   }

   /**
    * @param bundle Callback that provides bundle meta data to find use conflicts for
    * @param useConflictBundle Bundle that satisfies an import of the bundle that will not start
    * @param use Use Import that could be causing the use conflict
    * @param alreadyChecked List of bundles that have already been checked
    * @return All Use Conflicts found with the wiring of the potential use conflict bundle
    */
   private List<UseConflict> getWiringUseConflicts( IFindUseConflictsCallback callback, Bundle useConflictBundle, ImportedPackage use, List<Bundle> alreadyChecked ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      if( !alreadyChecked.contains( useConflictBundle ) ) {
         alreadyChecked.add( useConflictBundle );
         final List<ImportedPackage> importedPackages = callback.getManifest().getImportPackage().getImportedPackages();
         final ImportedPackage match = getMatchingImport( importedPackages, use );
         if( match != null ) {
            final PackageAdmin packageAdmin = getPackageAdmin();
            if( packageAdmin.resolveBundles( new Bundle[]{ useConflictBundle } ) ) {
               if( !BundleUtils.isImportedPackageResolved( bundleContext, useConflictBundle, use ) ) {
                  // ADD CONFLICT IF OPTIONAL PACKAGE NOT RESOLVED?
               }
               final BundleWire useConflictWire = BundleUtils.getBundleWire( bundleContext, useConflictBundle, use.getPackageName() );
               if( useConflictWire != null ) {
                  if( !BundleUtils.containsExportForImport( useConflictWire.getProviderWiring().getBundle(), match ) ) {
                     final com.springsource.util.osgi.manifest.ExportedPackage useConflictExportPackage = getExportedPackage( useConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                     result.add( new UseConflict( bundleContext, callback.getManifest(), match, useConflictBundle, useConflictExportPackage ) );
                  }
                  else {
                     final BundleWire wiring = callback.getBundleWire( match.getPackageName() );
                     if( wiring != null ) {
                        if( !wiring.getProviderWiring().getBundle().equals( useConflictWire.getProviderWiring().getBundle() ) ) {
                           final com.springsource.util.osgi.manifest.ExportedPackage useConflictExportPackage = getExportedPackage( useConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                           result.add( new UseConflict( bundleContext, callback.getManifest(), match, useConflictBundle, useConflictExportPackage ) );
                        }
                     }
                  }
               }
            }
         }
         else {
            final BundleWiring wiring = useConflictBundle.adapt( BundleWiring.class );
            if( wiring != null ) {
               for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
                  if( required.getProviderWiring() != null ) {
                     final Bundle secondaryUseConflictBundle = required.getProviderWiring().getBundle();
                     result.addAll( getWiringUseConflicts( callback, secondaryUseConflictBundle, use, alreadyChecked ) );
                  }
               }
            }
         }
      }
      return result;
   }
}
