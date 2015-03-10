package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings("deprecation")
public class BundleUtils {

   public static Bundle getBundleByNameOrId( BundleContext bundleContext, String bundleId ) {
      Bundle result = null;
      if( isLong( bundleId ) ) {
         result = bundleContext.getBundle( Long.valueOf( bundleId ) );
      }
      else {
         for( Bundle bundle : bundleContext.getBundles() ) {
            if( bundle.getSymbolicName().equals( bundleId ) ) {
               result = bundle;
               break;
            }
         }
      }
      return result;
   }

   public static boolean isBundleResolved( Bundle bundle ) {
      return Bundle.RESOLVED == bundle.getState() || Bundle.ACTIVE == bundle.getState();
   }

   public static boolean isImportedPackageResolved( BundleContext bundleContext, Bundle bundle, ImportedPackage importedPackage ) {
      boolean result = false;
      if( isBundleResolved( bundle ) ) {
         result = BundleUtils.getBundleWire( bundleContext, bundle, importedPackage.getPackageName() ) != null;
      }
      else {
         result = BundleUtils.findBestMatchThatSatisfiesImport( bundleContext, importedPackage ) != null;
      }
      return result;
   }

   private static boolean isLong( String value ) {
      boolean result = true;
      try {
         Long.parseLong( value );
      }
      catch( Throwable exception ) {
         result = false;
      }
      return result;
   }

   /**
    * Finds the {@link BundleWire} on the specified bundle for the package name
    * @param bundleContext Context to use to lookup OSGi services
    * @param bundle Bundle to find the {@link BundleWire} on
    * @param packageName Name of the import package to find the wiring for
    * @return {@link BundleWire} for the specified package or <code>null</code> if none found
    */
   public static BundleWire getBundleWire( BundleContext bundleContext, Bundle bundle, String packageName ) {
      try {
         BundleWire result = null;
         final PackageAdmin packageAdmin = getPackageAdmin( bundleContext );
         final BundleWiring wiring = bundle.adapt( BundleWiring.class );
         if( wiring != null ) {
            for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
               final org.osgi.service.packageadmin.ExportedPackage[] exportedPackages = packageAdmin.getExportedPackages( required.getProviderWiring().getBundle() );
               for( org.osgi.service.packageadmin.ExportedPackage exportedPackage : exportedPackages ) {
                  if( packageName.equals( exportedPackage.getName() ) ) {
                     result = required;
                     break;
                  }
               }
               if( result != null ) {
                  break;
               }
            }
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed to determine bundle wiring for bundle: %s Package: %s", bundle, packageName ), exception );
      }
   }

   /**
    * Finds {@link ExportedPackage} package from the bundle manifest that satisfies the specified {@link ImportedPackage}
    * @param bundle Bundle to check manifest exports
    * @param importedPackage Import to check if satisfied by an export in the specified {@link Bundle}
    * @return {@link ExportedPackage} from the bundle if it exists otherwise <code>null</code>
    */
   public static ExportedPackage getExportedPackage( Bundle bundle, ImportedPackage importedPackage ) {
      com.springsource.util.osgi.manifest.ExportedPackage result = null;
      final List<ExportedPackage> exportedPackages = getExportedPackages( bundle );
      for( ExportedPackage exportedPackage : exportedPackages ) {
         if( exportedPackage.getPackageName().equals( importedPackage.getPackageName() ) ) {
            if( importedPackage.getVersion().includes( exportedPackage.getVersion() ) ) {
               result = exportedPackage;
               break;
            }
         }
      }
      return result;
   }

   public static boolean containsExportForImport( Bundle bundle, ImportedPackage importedPackage ) {
      return getExportedPackage( bundle, importedPackage ) != null;
   }

   public static List<ImportedPackage> getImportedPackages( Bundle bundle ) {
      return getImportedPackages( bundle, null );
   }

   public static List<ImportedPackage> getImportedPackages( Bundle bundle, Resolution resolution ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         if( resolution == null || resolution.equals( importedPackage.getResolution() ) ) {
            result.add( importedPackage );
         }
      }
      return result;
   }

   public static Bundle findBestMatchThatSatisfiesImport( BundleContext bundleContext, ImportedPackage importedPackage ) {
      final List<Bundle> matches = findBundlesThatSatisfyImport( bundleContext, importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   public static List<Bundle> findBundlesThatSatisfyImport( BundleContext bundleContext, ImportedPackage importedPackage ) {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( containsExportForImport( bundle, importedPackage ) ) {
            result.add( bundle );
         }
      }
      Collections.sort( result );
      return result;
   }

   public static List<ExportedPackage> getExportedPackages( Bundle bundle ) {
      final List<ExportedPackage> result = new ArrayList<ExportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( ExportedPackage exportedPackage : manifest.getExportPackage().getExportedPackages() ) {
         result.add( exportedPackage );
      }
      return result;
   }

   public static PackageAdmin getPackageAdmin( BundleContext bundleContext ) {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }
}
