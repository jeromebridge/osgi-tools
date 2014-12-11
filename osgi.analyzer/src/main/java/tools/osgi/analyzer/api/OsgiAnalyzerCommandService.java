package tools.osgi.analyzer.api;

import java.util.List;
import java.util.Set;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;

import com.springsource.util.osgi.manifest.ImportedPackage;

public class OsgiAnalyzerCommandService {

   private BundleContext bundleContext;

   public OsgiAnalyzerCommandService( BundleContext bundleContext ) {
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

   @Descriptor("Diagnoses potential issues with class")
   public void diagnose_class(
         @Descriptor("Verbose output of diagnosis") @Parameter(
               names = { "-v", "--verbose" },
               presentValue = "true",
               absentValue = "false") boolean verbose,
         @Descriptor("Class name that got NoClassDefFoundError") String className
         ) {
      try {
         System.out.println( "Class:" + className );
         final List<Bundle> bundles = getOsgiAnalyzerService().getBundleForClassName( className );
         for( Bundle sourceBundle : bundles ) {
            System.out.println( String.format( "Providing Bundle: %s(%s)", sourceBundle.getSymbolicName(), sourceBundle.getBundleId() ) );
            for( Bundle refBundle : getOsgiAnalyzerService().getDependentBundles( sourceBundle ) ) {
               final BundleWiring wiring = refBundle.adapt( BundleWiring.class );
               final ClassLoader loader = wiring != null ? wiring.getClassLoader() : null;
               try {
                  refBundle.loadClass( className );
                  System.out.println( String.format( "  %s(%s) successfully loaded class with classloader: %s", refBundle.getSymbolicName(), refBundle.getBundleId(), loader ) );
               }
               catch( Exception exception ) {
                  System.out.println( String.format( "  %s(%s) failed to load class with classloader, %s", refBundle.getSymbolicName(), refBundle.getBundleId(), loader ) );
                  if( verbose ) {
                     exception.printStackTrace();
                  }

                  if( wiring != null ) {
                     //                     wiring.getClassLoader().get
                  }

               }
            }
         }

         // MBean Example
         Set<ObjectInstance> beans = getMBeanServer().queryMBeans( null, null );
         for( ObjectInstance instance : beans ) {
            @SuppressWarnings("unused")
            final MBeanInfo info = getMBeanServer().getMBeanInfo( instance.getObjectName() );
            // System.out.println( info.getDescriptor() );
         }

         // Removal Pending
         final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
         for( Bundle removalPending : fw.getRemovalPendingBundles() ) {
            System.out.println( String.format( "Removal Pending: %s(%s)", removalPending.getSymbolicName(), removalPending.getBundleId() ) );
         }
      }
      catch( Exception exception ) {
         exception.printStackTrace();
         throw new RuntimeException( String.format( "Error diagnosing NoClassDefFoundError for class: %s", className ), exception );
      }
   }

   private MBeanServer getMBeanServer() {
      final ServiceTracker<MBeanServer, Object> packageAdminTracker = new ServiceTracker<MBeanServer, Object>( bundleContext, MBeanServer.class.getName(), null );
      packageAdminTracker.open();
      final MBeanServer result = ( MBeanServer )packageAdminTracker.getService();
      return result;
   }

   private PackageAdmin getPackageAdmin() {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin result = ( PackageAdmin )packageAdminTracker.getService();
      return result;
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

   private IOsgiAnalyzerService getOsgiAnalyzerService() {
      final ServiceTracker<IOsgiAnalyzerService, Object> tracker = new ServiceTracker<IOsgiAnalyzerService, Object>( bundleContext, IOsgiAnalyzerService.class.getName(), null );
      tracker.open();
      final IOsgiAnalyzerService result = ( IOsgiAnalyzerService )tracker.getService();
      return result;
   }

   private void printBundlesWithMissingDependencies() {
      final List<Bundle> missingDependencies = getOsgiAnalyzerService().findBundlesWithMissingOptionalImports();
      if( missingDependencies.size() > 0 ) {
         final String format = "| %1$-35s|%2$10s |%3$25s |";
         final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Bundle", "Bundle ID", "Missing Optional Imports" ) );
         System.out.println( line );
         for( Bundle bundle : missingDependencies ) {
            final String bundleNameRaw = bundle.getSymbolicName();
            final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
            final Long bundleId = bundle.getBundleId();
            final int numOfMissingDependencies = getOsgiAnalyzerService().findMissingOptionalImports( bundle ).size();
            System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
         }
         System.out.println( line );
      }
   }

   private void printBundlesWithUseConflicts() {
      final List<Bundle> useConflicts = getOsgiAnalyzerService().findBundlesWithUseConflicts();
      if( useConflicts.size() > 0 ) {
         final String format = "| %1$-35s|%2$10s |%3$25s |";
         final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Bundle", "Bundle ID", "Use Conflicts" ) );
         System.out.println( line );
         for( Bundle bundle : useConflicts ) {
            final String bundleNameRaw = bundle.getSymbolicName();
            final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
            final Long bundleId = bundle.getBundleId();
            final int numOfMissingDependencies = getOsgiAnalyzerService().findUseConflicts( bundle ).size();
            System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
         }
         System.out.println( line );
      }
   }

   private void printUnresolvedImports( Bundle bundle ) {
      final List<ImportedPackage> unresolvedImports = getOsgiAnalyzerService().findMissingOptionalImports( bundle );
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
      final List<UseConflict> useConflicts = getOsgiAnalyzerService().findUseConflicts( bundle );
      if( useConflicts.size() > 0 ) {
         final String format = "| %1$-10s| %2$-45s| %3$-35s|%4$20s | %5$-28s| %6$-28s|";
         final String line = new String( new char[String.format( format, "", "", "", "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Type", "Import", "Conflict Bundle", "Conflict Bundle ID", "Import Version", "Conflict Version" ) );
         System.out.println( line );
         for( UseConflict useConflict : useConflicts ) {
            final String type = useConflict.getType().name();
            final String importPackageName = useConflict.getImportedPackage().getPackageName();
            final String conflictBundle = useConflict.getUseConflictBundle().getSymbolicName();
            final String conflictBundleId = Long.toString( useConflict.getUseConflictBundle().getBundleId() );
            final String importVersion = useConflict.getImportedPackage().getVersion().toString();
            final String conflictVersion = UseConflictType.Header.equals( useConflict.getType() ) ? useConflict.getUseConflictImportedPackage().getVersion().toParseString() : useConflict.getUseConflictExportedPackage().getVersion().toString();
            System.out.println( String.format( format, type, importPackageName, conflictBundle, conflictBundleId, importVersion, conflictVersion ) );
         }
         System.out.println( line );
      }
   }

}
