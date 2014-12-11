package tools.osgi.analyzer.internal;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import tools.osgi.analyzer.api.IOsgiAnalyzerService;
import tools.osgi.analyzer.api.UseConflict;

import com.springsource.util.osgi.VersionRange;
import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings("deprecation")
public class InternalOsgiAnalyzerService implements IOsgiAnalyzerService, UncaughtExceptionHandler {

   private BundleContext bundleContext;
   private UncaughtExceptionHandler oldHandler;

   public InternalOsgiAnalyzerService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   public void start() {
      oldHandler = Thread.getDefaultUncaughtExceptionHandler();
      Thread.setDefaultUncaughtExceptionHandler( this );
   }

   public void stop() {
      Thread.setDefaultUncaughtExceptionHandler( oldHandler );
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
   public List<ImportedPackage> findMissingOptionalImports( Bundle bundle ) {
      return getUnresolvedOptionalImportedPackages( bundle );
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

   private BundleWire getBundleWire( Bundle bundle, String packageName ) {
      try {
         BundleWire result = null;
         final PackageAdmin packageAdmin = getPackageAdmin();
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

   /**
    * get all possible class instance available in the OSGi container for a
    * distinct full qualified class name.
    *
    * @param clazzName
    *            full qualified class name like java.lang.Object
    * @return guarantied to be not null (but might be empty though)
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

   private PackageAdmin getPackageAdmin() {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
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
      final PackageAdmin packageAdmin = getPackageAdmin();
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
         final PackageAdmin packageAdmin = getPackageAdmin();
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

   @Override
   public void diagnose( Throwable exception ) {
      // TODO Auto-generated method stub
      System.out.println( "DIAGNOSE ME!!!!" );
   }

   @Override
   public void uncaughtException( Thread thread, Throwable exception ) {

      if( oldHandler != null ) {
         oldHandler.uncaughtException( thread, exception );
      }
   }
}
