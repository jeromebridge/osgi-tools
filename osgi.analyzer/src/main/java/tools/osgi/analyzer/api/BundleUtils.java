package tools.osgi.analyzer.api;

import java.util.ArrayList;
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
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings("deprecation")
public class BundleUtils {

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
