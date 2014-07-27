package tools.osgi.analyzer.internal;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import tools.osgi.analyzer.OsgiAnalyzerService;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

@SuppressWarnings("deprecation")
public class Activator implements BundleActivator {

   @Override
   public void start( BundleContext context ) throws Exception {
      try {
         final Hashtable<String, Object> props = new Hashtable<String, Object>();
         props.put( "osgi.command.scope", "tools" );
         props.put( "osgi.command.function", new String[]{ "analyze" } );         
         context.registerService( OsgiAnalyzerService.class.getName(), new OsgiAnalyzerService( context ), props );

         
         
         
         for( Bundle bundle : context.getBundles() ) {
            if( isRefreshRequired( bundle ) ) {
               System.out.println( "Bundle: " + bundle.getSymbolicName() + " (" + bundle.getBundleId() + ")" + " Unresolved Optional Imports: " + getUnresolvedOptionalImportedPackages( bundle ).size() );
            }
         }
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error starting bundle: %s, Error: %s", context.getBundle().getSymbolicName(), exception.getMessage() ), exception );
      }
   }
   
   

   @Override
   public void stop( BundleContext context ) throws Exception {

   }

   private boolean isRefreshRequired( Bundle bundle ) {
      return getUnresolvedOptionalImportedPackages( bundle ).size() > 0;
   }

   private List<ImportedPackage> getOptionalImportedPackages( Bundle bundle ) {
      final List<ImportedPackage> result = new ArrayList<ImportedPackage>();
      final BundleManifest manifest = BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         if( Resolution.OPTIONAL.equals( importedPackage.getResolution() ) ) {
            result.add( importedPackage );
         }
      }
      return result;
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
         if( bundle.getBundleContext() != null ) {
            final PackageAdmin packageAdmin = getPackageAdmin( bundle.getBundleContext() );
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
         }
         else {
            result = true;
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed to determine import package resolved for bundle: %s Package: %s", bundle, importedPackage ), exception );
      }
   }

   private PackageAdmin getPackageAdmin( BundleContext context ) {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( context, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }

}
