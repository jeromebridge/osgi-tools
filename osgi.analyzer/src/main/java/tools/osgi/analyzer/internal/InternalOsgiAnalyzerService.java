package tools.osgi.analyzer.internal;

import java.io.Reader;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Dictionary;
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
import tools.osgi.analyzer.internal.FindUseConflictsTemplate.BundleFindUseConflictsCallback;
import tools.osgi.analyzer.internal.FindUseConflictsTemplate.BundleManifestFindUseConflictsCallback;

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
   public List<UseConflict> findUseConflicts( Dictionary<String, String> headers ) {
      return getUseConflicts( BundleManifestFactory.createBundleManifest( headers, new DummyParserLogger() ) );
   }

   @Override
   public List<UseConflict> findUseConflicts( Reader headers ) {
      try {
         return getUseConflicts( BundleManifestFactory.createBundleManifest( headers, new DummyParserLogger() ) );
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Error trying to find use conflicts for manifest headers", exception );
      }
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

   private MissingImport getMissingImport( Bundle bundle, ImportedPackage importedPackage ) {
      final MissingImport result = new MissingImport( importedPackage );
      result.setReason( MissingOptionalImportReasonType.Unknown );
      final Bundle match = BundleUtils.findBestMatchThatSatisfiesImport( bundleContext, importedPackage );
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
         if( !BundleUtils.isImportedPackageResolved( bundleContext, bundle, importedPackage ) ) {
            result.add( getMissingImport( bundle, importedPackage ) );
         }
      }
      return result;
   }

   private List<UseConflict> getUseConflicts( Bundle bundle ) {
      return new FindUseConflictsTemplate( bundleContext ).find( new BundleFindUseConflictsCallback( bundle ) );
   }

   private List<UseConflict> getUseConflicts( Bundle bundle, ImportedPackage importedPackage ) {
      return new FindUseConflictsTemplate( bundleContext ).find( new BundleFindUseConflictsCallback( bundle ), importedPackage );
   }

   private List<UseConflict> getUseConflicts( BundleManifest manifest ) {
      return new FindUseConflictsTemplate( bundleContext ).find( new BundleManifestFindUseConflictsCallback( manifest ) );
   }

   private boolean hasUseConflict( Bundle bundle ) {
      return getUseConflicts( bundle ).size() > 0;
   }

   private boolean isRefreshRequired( Bundle bundle ) {
      return getUnresolvedImportedPackages( bundle ).size() > 0;
   }
}
