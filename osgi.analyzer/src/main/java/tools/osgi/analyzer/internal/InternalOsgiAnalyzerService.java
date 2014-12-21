package tools.osgi.analyzer.internal;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import tools.osgi.analyzer.api.BundleUtils;
import tools.osgi.analyzer.api.IOsgiAnalyzerService;
import tools.osgi.analyzer.api.MissingImport;
import tools.osgi.analyzer.api.MissingOptionalImportReasonType;
import tools.osgi.analyzer.api.UseConflict;

import com.springsource.util.osgi.VersionRange;
import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings({ "deprecation" })
public class InternalOsgiAnalyzerService implements IOsgiAnalyzerService, UncaughtExceptionHandler {

   public static class BundleImportedPackage {
      private Bundle bundle;
      private ImportedPackage importedPackage;

      public BundleImportedPackage( Bundle bundle, ImportedPackage importedPackage ) {
         setBundle( bundle );
         setImportedPackage( importedPackage );
      }

      @Override
      public boolean equals( Object other ) {
         boolean result = other != null && other instanceof BundleImportedPackage;
         if( result ) {
            final BundleImportedPackage otherPackage = ( BundleImportedPackage )other;
            result = result && otherPackage.getBundle() != null && otherPackage.getBundle().equals( bundle );
            result = result && otherPackage.getImportedPackage() != null && otherPackage.getImportedPackage().equals( importedPackage );
         }
         return result;
      }

      public Bundle getBundle() {
         return bundle;
      }

      public ImportedPackage getImportedPackage() {
         return importedPackage;
      }

      public void setBundle( Bundle bundle ) {
         this.bundle = bundle;
      }

      public void setImportedPackage( ImportedPackage importedPackage ) {
         this.importedPackage = importedPackage;
      }

      @Override
      public String toString() {
         final String bundleDesc = bundle != null ? bundle.getSymbolicName() : "[NONE]";
         final String importDesc = importedPackage != null ? importedPackage.getPackageName() : "[NONE]";
         return String.format( "Bundle: %s, Import: %s", bundleDesc, importDesc );
      }
   }

   private BundleContext bundleContext;
   private UncaughtExceptionHandler oldHandler;

   public InternalOsgiAnalyzerService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   @Override
   public void diagnose( Throwable exception ) {
      // TODO Auto-generated method stub
      System.out.println( "DIAGNOSE ME!!!!" );
   }

   @Override
   public List<Bundle> findBundlesWithMissingOptionalImports() {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( isRefreshRequired( bundle ) ) {
            result.add( bundle );
         }
      }
      return result;
   }

   @Override
   public List<Bundle> findBundlesWithUseConflicts() {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( hasUseConflict( bundle ) ) {
            result.add( bundle );
         }
      }
      return result;
   }

   @Override
   public List<MissingImport> findMissingOptionalImports( Bundle bundle ) {
      return getUnresolvedImportedPackages( bundle );
   }

   @Override
   public List<UseConflict> findUseConflicts( Bundle bundle ) {
      return getUseConflicts( bundle );
   }

   @Override
   public Bundle getBundleForClass( final Class<?> clazz ) {
      PackageAdmin admin = getPackageAdmin();
      if( admin != null ) {
         Bundle b = admin.getBundle( clazz );
         if( b == null ) {
            // must be the system bundle
            return bundleContext.getBundle( 0 );
         }
         else {
            return b;
         }
      }
      return null;
   }

   @Override
   public List<Bundle> getBundleForClassName( final String fqcn ) {
      final List<Bundle> result = new ArrayList<Bundle>();
      final List<Class<?>> classes = getClassesForName( fqcn );
      for( Class<?> clazz : classes ) {
         result.add( getBundleForClass( clazz ) );
      }
      return result;
   }

   @Override
   public List<Bundle> getDependentBundles( Bundle bundle ) {
      final Set<Bundle> result = new HashSet<Bundle>();
      final BundleWiring wiring = bundle.adapt( BundleWiring.class );
      for( BundleWire provided : wiring.getProvidedWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
         result.add( provided.getRequirerWiring().getBundle() );
      }
      return new ArrayList<Bundle>( result );
   }

   public void start() {
      oldHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler( this );
   }

   public void stop() {
      Thread.setDefaultUncaughtExceptionHandler( oldHandler );
   }

   @Override
   public void uncaughtException( Thread thread, Throwable exception ) {

      if( oldHandler != null ) {
         oldHandler.uncaughtException( thread, exception );
      }
   }

   private boolean containsExportForImport( Bundle bundle, ImportedPackage importedPackage ) {
      return BundleUtils.getExportedPackage( bundle, importedPackage ) != null;
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
      Collections.sort( result );
      return result;
   }

   /**
    * get all possible class instance available in the OSGi container for a
    * distinct full qualified class name.
    *
    * @param clazzName
    *            full qualified class name like java.lang.Object
    * @return guaranteed to be not null (but might be empty though)
    * @version 1.0
    * @since 1.0
    */
   private List<Class<?>> getClassesForName( final String clazzName ) {
      Bundle[] bundles = bundleContext.getBundles();
      HashSet<Class<?>> classes = new HashSet<Class<?>>();
      for( int i = 0; i < bundles.length; i++ ) {
         // check if you can successfully load the class
         try {
            classes.add( bundles[i].loadClass( clazzName ) );
            // add the class!!! (not the bundle) to the list of possible
            // providers (bundle can delegate class loading to other bundles
            // so only the Class - FQCN plus CL - is unique, thus use the
            // HashSet to filter multiple providers of the same Class.
         }
         catch( ClassNotFoundException e ) {
            // indicates no class available in this bundle -> do nothing!
         }
      }
      return new ArrayList<Class<?>>( classes );
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

   private List<UseConflict> getHeaderUseConflicts( Bundle bundle, Bundle useConflictBundle, ImportedPackage use ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
      final ImportedPackage match = getMatchingImport( importedPackages, use );
      if( match != null ) {
         if( VersionRange.intersection( match.getVersion(), use.getVersion() ).isEmpty() ) {
            result.add( new UseConflict( bundleContext, bundle, match, useConflictBundle, use ) );
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

   private ImportedPackage getImportedPackageForBundleWire( Bundle bundle, BundleWire required ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : getImportedPackages( bundle ) ) {
         final String filter = required.getRequirement().getDirectives().get( "filter" );
         final String packageFilter = String.format( "(osgi.wiring.package=%s)", importedPackage.getPackageName() );
         if( filter != null && filter.contains( packageFilter ) ) {
            result = importedPackage;
            break;
         }
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

   private ImportedPackage getMatchingImport( List<ImportedPackage> importedPackages, ImportedPackage use ) {
      ImportedPackage result = null;
      for( ImportedPackage importedPackage : importedPackages ) {
         if( importedPackage.getPackageName().equals( use.getPackageName() ) ) {
            result = importedPackage;
         }
      }
      return result;
   }

   private MissingImport getMissingImport( Bundle bundle, ImportedPackage importedPackage ) {
      final MissingImport result = new MissingImport( importedPackage );
      result.setReason( MissingOptionalImportReasonType.Unknown );
      final Bundle match = findBestMatchThatSatisfiesImport( importedPackage );
      if( match != null ) {
         result.setReason( MissingOptionalImportReasonType.RefreshRequired );
         result.setMatch( match );
         final List<UseConflict> useConflicts = getUseConflicts( bundle, importedPackage );
         if( !useConflicts.isEmpty() ) {
            result.setReason( MissingOptionalImportReasonType.UseConflict );
            result.setUseConflicts( useConflicts );
         }
      }
      else {
         result.setReason( MissingOptionalImportReasonType.Unavailable );
      }
      return result;
   }

   @SuppressWarnings("unused")
   private List<ImportedPackage> getOptionalImportedPackages( Bundle bundle ) {
      return getImportedPackages( bundle, Resolution.OPTIONAL );
   }

   private PackageAdmin getPackageAdmin() {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }

   private List<MissingImport> getUnresolvedImportedPackages( Bundle bundle ) {
      final List<MissingImport> result = new ArrayList<MissingImport>();
      for( ImportedPackage importedPackage : getImportedPackages( bundle ) ) {
         if( !isImportedPackageResolved( bundle, importedPackage ) ) {
            result.add( getMissingImport( bundle, importedPackage ) );
         }
      }
      return result;
   }

   private List<UseConflict> getUseConflicts( Bundle bundle ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final PackageAdmin packageAdmin = getPackageAdmin();
      if( !packageAdmin.resolveBundles( new Bundle[]{ bundle } ) ) {
         final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
         for( ImportedPackage importedPackage : importedPackages ) {
            result.addAll( getUseConflicts( bundle, importedPackage ) );
         }
      }
      return result;
   }

   /**
    * @param bundle Bundle being checked for use conflicts
    * @param useConflictBundleImport Bundle and Import Package down the dependency chain of the bundle being checked
    * @param alreadyChecked List of bundle/imports that have already been checked
    * @return List of use conflicts for the bundle combination
    */
   private List<UseConflict> getUseConflicts( Bundle bundle, BundleImportedPackage useConflictBundleImport, List<BundleImportedPackage> alreadyChecked ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      if( useConflictBundleImport.getBundle() != null && useConflictBundleImport.getImportedPackage() != null && !alreadyChecked.contains( useConflictBundleImport ) ) {
         alreadyChecked.add( useConflictBundleImport );

         final com.springsource.util.osgi.manifest.ExportedPackage exportedPackage = BundleUtils.getExportedPackage( useConflictBundleImport.getBundle(), useConflictBundleImport.getImportedPackage() );
         final List<ImportedPackage> uses = getImportedPackagesForExportUses( useConflictBundleImport.getBundle(), exportedPackage );
         for( ImportedPackage use : uses ) {
            result.addAll( getHeaderUseConflicts( bundle, useConflictBundleImport.getBundle(), use ) );
            result.addAll( getWiringUseConflicts( bundle, useConflictBundleImport.getBundle(), use ) );
         }

         // Secondary Dependencies
         final BundleWiring wiring = useConflictBundleImport.getBundle().adapt( BundleWiring.class );
         if( wiring != null ) {
            for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
               if( required.getProviderWiring() != null ) {
                  final Bundle secondaryUseConflictBundle = required.getProviderWiring().getBundle();
                  final ImportedPackage secondaryImportedPackage = getImportedPackageForBundleWire( useConflictBundleImport.getBundle(), required );
                  final BundleImportedPackage secondaryBundleImport = new BundleImportedPackage( secondaryUseConflictBundle, secondaryImportedPackage );
                  result.addAll( getUseConflicts( bundle, secondaryBundleImport, alreadyChecked ) );
               }
            }
         }
      }
      return result;
   }

   /**
    * @param bundle Bundle to find use conflicts on
    * @param importedPackage Import Package from the bundle to check for conflicts down the dependency tree
    * @param alreadyChecked Bundles that are already checked
    * @return List of all Use Conflicts found recursively
    */
   private List<UseConflict> getUseConflicts( Bundle bundle, ImportedPackage importedPackage ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      final Bundle match = findBestMatchThatSatisfiesImport( importedPackage );
      if( match != null ) {
         result.addAll( getUseConflicts( bundle, new BundleImportedPackage( match, importedPackage ), new ArrayList<BundleImportedPackage>() ) );
      }
      return result;
   }

   private List<UseConflict> getWiringUseConflicts( Bundle bundle, Bundle useConflictBundle, ImportedPackage use ) {
      return getWiringUseConflicts( bundle, useConflictBundle, use, new ArrayList<Bundle>() );
   }

   /**
    * @param bundle Bundle that will not start because of a use conflict
    * @param useConflictBundle Bundle that satisfies an import of the bundle that will not start
    * @param use Use Import that could be causing the use conflict
    * @param alreadyChecked List of bundles that have already been checked
    * @return All Use Conflicts found with the wiring of the potential use conflict bundle
    */
   private List<UseConflict> getWiringUseConflicts( Bundle bundle, Bundle useConflictBundle, ImportedPackage use, List<Bundle> alreadyChecked ) {
      final List<UseConflict> result = new ArrayList<UseConflict>();
      if( !alreadyChecked.contains( useConflictBundle ) ) {
         alreadyChecked.add( useConflictBundle );
         final List<ImportedPackage> importedPackages = getImportedPackages( bundle );
         final ImportedPackage match = getMatchingImport( importedPackages, use );
         if( match != null ) {
            final PackageAdmin packageAdmin = getPackageAdmin();
            if( packageAdmin.resolveBundles( new Bundle[]{ useConflictBundle } ) ) {
               if( !isImportedPackageResolved( useConflictBundle, use ) ) {
                  // ADD CONFLICT IF OPTIONAL PACKAGE NOT RESOLVED?
               }
               final BundleWire useConflictWire = BundleUtils.getBundleWire( bundleContext, useConflictBundle, use.getPackageName() );
               if( useConflictWire != null ) {
                  if( !containsExportForImport( useConflictWire.getProviderWiring().getBundle(), match ) ) {
                     final com.springsource.util.osgi.manifest.ExportedPackage useConflictExportPackage = getExportedPackage( useConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                     result.add( new UseConflict( bundleContext, bundle, match, useConflictBundle, useConflictExportPackage ) );
                  }
                  else {
                     final BundleWire wiring = BundleUtils.getBundleWire( bundleContext, bundle, match.getPackageName() );
                     if( wiring != null ) {
                        if( !wiring.getProviderWiring().getBundle().equals( useConflictWire.getProviderWiring().getBundle() ) ) {
                           final com.springsource.util.osgi.manifest.ExportedPackage useConflictExportPackage = getExportedPackage( useConflictWire.getProviderWiring().getBundle(), match.getPackageName() );
                           result.add( new UseConflict( bundleContext, bundle, match, useConflictBundle, useConflictExportPackage ) );
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
                     result.addAll( getWiringUseConflicts( bundle, secondaryUseConflictBundle, use, alreadyChecked ) );
                  }
               }
            }
         }
      }
      return result;
   }

   private boolean hasUseConflict( Bundle bundle ) {
      return getUseConflicts( bundle ).size() > 0;
   }

   private boolean isBundleResolved( Bundle bundle ) {
      return Bundle.RESOLVED == bundle.getState() || Bundle.ACTIVE == bundle.getState();
   }

   private boolean isImportedPackageResolved( Bundle bundle, ImportedPackage importedPackage ) {
      boolean result = false;
      if( isBundleResolved( bundle ) ) {
         result = BundleUtils.getBundleWire( bundleContext, bundle, importedPackage.getPackageName() ) != null;
      }
      else {
         result = findBestMatchThatSatisfiesImport( importedPackage ) != null;
      }
      return result;
   }

   private boolean isRefreshRequired( Bundle bundle ) {
      return getUnresolvedImportedPackages( bundle ).size() > 0;
   }
}
