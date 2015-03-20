package tools.osgi.analyzer.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.util.tracker.ServiceTracker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

// install -start assembly:/home/developer/git/osgi-tools/osgi.analyzer/bin/maven/classes

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
         @Descriptor("Find all bundles with uses conflicts") @Parameter(
               names = { "-u", "--use-conflicts" },
               presentValue = "true",
               absentValue = "false") boolean includeUsesConflicts,
         @Descriptor("Removal Pending bundles") @Parameter(
               names = { "-r", "--removal-pending" },
               presentValue = "true",
               absentValue = "false") boolean includeRemovalPending,
         @Descriptor("Find all issues with bundles") @Parameter(
               names = { "-a", "--all" },
               presentValue = "true",
               absentValue = "false") boolean includeAll
         ) {
      try {
         if( includeMissingDependencies || includeAll ) {
            printBundlesWithMissingDependencies();
         }
         if( includeUsesConflicts || includeAll ) {
            printBundlesWithUsesConflicts();
         }
         if( includeRemovalPending || includeAll ) {
            printBundlesThatAreRemovalPending();
         }
      }
      catch( Exception exception ) {
         exception.printStackTrace();
         throw new RuntimeException( String.format( "Error analyzing OSGi container" ), exception );
      }
   }

   @Descriptor("Diagnoses issues with a specified bundle")
   public void diagnose(
         @Descriptor("Print verbose messages") @Parameter(
               names = { "-v", "--verbose" },
               presentValue = "true",
               absentValue = "false") boolean verbose,
         @Descriptor("Bundle ID to diagnose issues") String bundleId
         ) {
      try {
         // Get Bundle
         final Bundle bundle = BundleUtils.getBundleByNameOrId( bundleContext, bundleId );
         if( bundle == null ) {
            throw new IllegalArgumentException( String.format( "No bundle could be found for %s", bundleId ) );
         }

         // Print
         System.out.println( "Bundle: " + bundle );
         printUnresolvedImports( bundle, verbose );
         printUsesConflicts( bundle, verbose );
      }
      catch( Throwable exception ) {
         exception.printStackTrace();
         throw new RuntimeException( String.format( "Error diagnosing bundle: %s", bundleId ), exception );
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
         System.out.println( "Class: " + className );
         final List<Bundle> bundles = getOsgiAnalyzerService().getBundleForClassName( className );
         final List<Bundle> success = new ArrayList<Bundle>();
         final List<Bundle> failure = new ArrayList<Bundle>();
         if( !bundles.isEmpty() ) {
            for( Bundle sourceBundle : bundles ) {
               System.out.println( String.format( "Providing Bundle: %s(%s)", sourceBundle.getSymbolicName(), sourceBundle.getBundleId() ) );
               for( Bundle refBundle : getOsgiAnalyzerService().getDependentBundles( sourceBundle ) ) {
                  final BundleWiring wiring = refBundle.adapt( BundleWiring.class );
                  @SuppressWarnings("unused")
                  final ClassLoader loader = wiring != null ? wiring.getClassLoader() : null;
                  try {
                     refBundle.loadClass( className );
                     success.add( refBundle );
                  }
                  catch( Exception exception ) {
                     failure.add( refBundle );
                     if( verbose ) {
                        exception.printStackTrace();
                     }

                     if( wiring != null ) {
                        //                     wiring.getClassLoader().get
                     }

                  }
               }
            }
         }
         else {
            System.out.println( String.format( "No Providing Bundles Found for: %s", className ) );
         }

         // Print
         final String line = "==========================================================";
         if( !success.isEmpty() ) {
            System.out.println( "" );
            System.out.println( "Successful" );
            System.out.println( line );
            for( Bundle refBundle : success ) {
               final BundleWiring wiring = refBundle.adapt( BundleWiring.class );
               final ClassLoader loader = wiring != null ? wiring.getClassLoader() : null;
               System.out.println( String.format( "  %s(%s) successfully loaded class with classloader: %s", refBundle.getSymbolicName(), refBundle.getBundleId(), loader ) );
            }
         }
         if( !failure.isEmpty() ) {
            System.out.println( "" );
            System.out.println( "Failure" );
            System.out.println( line );
            for( Bundle refBundle : failure ) {
               final BundleWiring wiring = refBundle.adapt( BundleWiring.class );
               final ClassLoader loader = wiring != null ? wiring.getClassLoader() : null;
               System.out.println( String.format( "  %s(%s) failed to load class with classloader, %s", refBundle.getSymbolicName(), refBundle.getBundleId(), loader ) );
            }
            System.out.println( "" );
         }

         // MBean Example
         Set<ObjectInstance> beans = getMBeanServer().queryMBeans( null, null );
         for( ObjectInstance instance : beans ) {
            @SuppressWarnings("unused")
            final MBeanInfo info = getMBeanServer().getMBeanInfo( instance.getObjectName() );
            // System.out.println( info.getDescriptor() );
         }

         //         // Removal Pending
         //         final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
         //         for( Bundle removalPending : fw.getRemovalPendingBundles() ) {
         //            System.out.println( String.format( "Removal Pending: %s(%s)", removalPending.getSymbolicName(), removalPending.getBundleId() ) );
         //         }
      }
      catch( Exception exception ) {
         exception.printStackTrace();
         throw new RuntimeException( String.format( "Error diagnosing NoClassDefFoundError for class: %s", className ), exception );
      }
   }

   @Descriptor("Inspects aspects of a given bundle")
   public void inspect(
         @Descriptor("Print verbose messages") @Parameter(
               names = { "-v", "--verbose" },
               presentValue = "true",
               absentValue = "false") boolean verbose,
         @Descriptor("Bundle ID to diagnose issues") String bundleId
         ) {
      try {
         final Bundle bundle = BundleUtils.getBundleByNameOrId( bundleContext, bundleId );
         if( bundle == null ) {
            throw new IllegalArgumentException( String.format( "No bundle could be found for %s", bundleId ) );
         }

         final String line = "======================================================";
         final String subline = "------------------------------------------------------";
         System.out.println( "" );
         System.out.println( String.format( "%s(%s)", bundle.getSymbolicName(), bundle.getBundleId() ) );
         System.out.println( line );
         System.out.println( String.format( "Application Context" ) );
         System.out.println( subline );
         final ApplicationContext applicationContext = getBundleApplicationContext( bundle );

         if( applicationContext instanceof AbstractApplicationContext ) {
            @SuppressWarnings("resource")
            final AbstractApplicationContext applicationContext2 = ( AbstractApplicationContext )applicationContext;
            System.out.println( String.format( "Active: %s", applicationContext2.isActive() ) );
         }

         if( applicationContext != null ) {
            for( String beanName : applicationContext.getBeanDefinitionNames() ) {
               System.out.println( beanName );
            }
         }
         else {
            System.out.println( "[NO APPLICATION CONTEXT FOUND]" );
         }
      }
      catch( Exception exception ) {
         exception.printStackTrace();
         throw new RuntimeException( String.format( "Error inspecting bundle: %s", bundleId ), exception );
      }
   }

   private ServiceTracker<ApplicationContext, Object> getApplicationContextServiceTracker() {
      final ServiceTracker<ApplicationContext, Object> tracker = new ServiceTracker<ApplicationContext, Object>( bundleContext, ApplicationContext.class.getName(), null );
      tracker.open();
      return tracker;
   }

   private ApplicationContext getBundleApplicationContext( Bundle bundle ) {
      ApplicationContext result = null;
      final ServiceTracker<ApplicationContext, Object> tracker = getApplicationContextServiceTracker();
      if( tracker.getServiceReferences() != null ) {
         for( ServiceReference<ApplicationContext> ref : tracker.getServiceReferences() ) {
            if( bundle.equals( ref.getBundle() ) ) {
               result = ( ApplicationContext )tracker.getService( ref );
               break;
            }
         }
      }
      return result;
   }

   private MBeanServer getMBeanServer() {
      final ServiceTracker<MBeanServer, Object> packageAdminTracker = new ServiceTracker<MBeanServer, Object>( bundleContext, MBeanServer.class.getName(), null );
      packageAdminTracker.open();
      final MBeanServer result = ( MBeanServer )packageAdminTracker.getService();
      return result;
   }

   private IOsgiAnalyzerService getOsgiAnalyzerService() {
      final ServiceTracker<IOsgiAnalyzerService, Object> tracker = new ServiceTracker<IOsgiAnalyzerService, Object>( bundleContext, IOsgiAnalyzerService.class.getName(), null );
      tracker.open();
      final IOsgiAnalyzerService result = ( IOsgiAnalyzerService )tracker.getService();
      return result;
   }

   private void printBundlesThatAreRemovalPending() {
      final String format = "| %1$-35s|%2$10s |%3$25s |";
      final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
      final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
      if( !fw.getRemovalPendingBundles().isEmpty() ) {
         System.out.println( line );
         System.out.println( String.format( format, "Bundle", "Bundle ID", "State" ) );
         System.out.println( line );
         for( Bundle removalPending : fw.getRemovalPendingBundles() ) {
            final String bundleNameRaw = removalPending.getSymbolicName();
            final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
            final Long bundleId = removalPending.getBundleId();
            System.out.println( String.format( format, bundleName, bundleId, BundleUtils.getStateDescription( removalPending ) ) );
         }
      }
      for( Bundle bundle : bundleContext.getBundles() ) {
         final BundleWiring wiring = bundle.adapt( BundleWiring.class );
         if( wiring != null ) {
            for( BundleWire required : wiring.getRequiredWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
               if( !required.getProviderWiring().isCurrent() ) {
                  System.out.println( "Wire not current: " + required );
               }
            }

            if( !wiring.isCurrent() ) {
               System.out.println( String.format( "Bundle %s(%s) is not current", bundle.getSymbolicName(), bundle.getBundleId() ) );
            }
         }
      }
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

   private void printBundlesWithUsesConflicts() {
      final List<Bundle> usesConflicts = getOsgiAnalyzerService().findBundlesWithUsesConflicts();
      if( usesConflicts.size() > 0 ) {
         final String format = "| %1$-35s|%2$10s |%3$25s |";
         final String line = new String( new char[String.format( format, "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Bundle", "Bundle ID", "Use Conflicts" ) );
         System.out.println( line );
         for( Bundle bundle : usesConflicts ) {
            final String bundleNameRaw = bundle.getSymbolicName();
            final String bundleName = bundleNameRaw.substring( 0, Math.min( 34, bundleNameRaw.length() ) );
            final Long bundleId = bundle.getBundleId();
            final int numOfMissingDependencies = getOsgiAnalyzerService().findUsesConflicts( bundle ).size();
            System.out.println( String.format( format, bundleName, bundleId, numOfMissingDependencies ) );
         }
         System.out.println( line );
      }
   }

   private void printUnresolvedImports( Bundle bundle, boolean verbose ) {
      final List<MissingImport> unresolvedImports = getOsgiAnalyzerService().findMissingOptionalImports( bundle );
      if( unresolvedImports.size() > 0 ) {
         final String format = "| %1$-35s | %2$-15s | %3$-16s | %4$-50s |";
         final String line = new String( new char[String.format( format, "", "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Unresolved Import", "Version", "Reason", "Matching Bundle" ) );
         System.out.println( line );
         for( MissingImport missingOptionalImport : unresolvedImports ) {
            final String packageName = missingOptionalImport.getImportedPackage().getPackageName();
            final String version = missingOptionalImport.getImportedPackage().getVersion().toString();
            final String reason = missingOptionalImport.getReason().display();
            final String matchDesc = missingOptionalImport.getMatch() != null ? String.format( "%s(%s)", missingOptionalImport.getMatch().getSymbolicName(), missingOptionalImport.getMatch().getBundleId() ) : "[NONE]";
            System.out.println( String.format( format, packageName, version, reason, matchDesc ) );
         }
         System.out.println( line );
         System.out.println( "" );
         System.out.println( "" );

         final Set<UsesConflictResolutionSuggestion> suggestions = new HashSet<UsesConflictResolutionSuggestion>();
         for( MissingImport missingOptionalImport : unresolvedImports ) {
            if( missingOptionalImport.getReason().isPossibleResolutionAvailable() ) {
               if( verbose ) {
                  System.out.println( String.format( "Package: %s Reason: %s", missingOptionalImport.getImportedPackage().getPackageName(), missingOptionalImport.getReason().display() ) );
               }
               if( MissingOptionalImportReasonType.RefreshRequired.equals( missingOptionalImport.getReason() ) ) {
                  if( verbose ) {
                     System.out.println( String.format( "Resolution: Refresh %s(%s)", missingOptionalImport.getMatch().getSymbolicName(), missingOptionalImport.getMatch().getBundleId() ) );
                  }
               }
               else if( MissingOptionalImportReasonType.UsesConflict.equals( missingOptionalImport.getReason() ) ) {
                  if( verbose ) {
                     int usesConflictIndex = 1;
                     for( UsesConflict usesConflict : missingOptionalImport.getUsesConflicts() ) {
                        System.out.println( String.format( "Uses Conflict %s: %s(%s)", usesConflictIndex, usesConflict.getUsesConflictBundle().getSymbolicName(), usesConflict.getUsesConflictBundle().getBundleId() ) );
                        System.out.println( String.format( "   Type: %s", usesConflict.getType().name() ) );
                        System.out.println( String.format( "   Import Package: %s(%s)", usesConflict.getImportedPackage().getPackageName(), usesConflict.getImportedPackage().getVersion() ) );
                        if( UsesConflictType.Wiring.equals( usesConflict.getType() ) ) {
                           System.out.println( String.format( "   Bundle Wire: %s(%s)", usesConflict.getBundleWire().getProviderWiring().getBundle().getSymbolicName(), usesConflict.getBundleWire().getProviderWiring().getBundle().getBundleId() ) );
                           System.out.println( String.format( "   Uses Conflict Bundle Wire: %s(%s)", usesConflict.getUsesConflictBundleWire().getProviderWiring().getBundle().getSymbolicName(), usesConflict.getUsesConflictBundleWire().getProviderWiring().getBundle().getBundleId() ) );
                        }
                        usesConflictIndex++;
                     }
                  }
                  for( UsesConflict usesConflict : missingOptionalImport.getUsesConflicts() ) {
                     suggestions.add( usesConflict.getSuggestion() );
                  }
               }
            }
         }

         if( !suggestions.isEmpty() ) {
            System.out.println( line );
            System.out.println( String.format( "Possible Resolutions" ) );
            System.out.println( line );

            for( UsesConflictResolutionSuggestion suggestion : suggestions ) {
               System.out.println( String.format( "Suggestion: %s", suggestion ) );
            }
         }
      }
   }

   private void printUsesConflicts( Bundle bundle, boolean verbose ) {
      final List<UsesConflict> usesConflicts = getOsgiAnalyzerService().findUsesConflicts( bundle );
      if( usesConflicts.size() > 0 ) {
         final String format = "| %1$-10s| %2$-45s| %3$-35s|%4$20s | %5$-28s| %6$-28s|";
         final String line = new String( new char[String.format( format, "", "", "", "", "", "" ).length()] ).replace( "\0", "-" );
         System.out.println( line );
         System.out.println( String.format( format, "Type", "Import", "Conflict Bundle", "Conflict Bundle ID", "Import Version", "Conflict Version" ) );
         System.out.println( line );
         for( UsesConflict usesConflict : usesConflicts ) {
            final String type = usesConflict.getType().name();
            final String importPackageName = usesConflict.getImportedPackage().getPackageName();
            final String conflictBundle = usesConflict.getUsesConflictBundle().getSymbolicName();
            final String conflictBundleId = Long.toString( usesConflict.getUsesConflictBundle().getBundleId() );
            final String importVersion = usesConflict.getImportedPackage().getVersion().toString();
            final String conflictVersion = UsesConflictType.Header.equals( usesConflict.getType() ) ? usesConflict.getUsesConflictImportedPackage().getVersion().toParseString() : usesConflict.getUsesConflictExportedPackage().getVersion().toString();
            System.out.println( String.format( format, type, importPackageName, conflictBundle, conflictBundleId, importVersion, conflictVersion ) );
         }
         System.out.println( line );
      }
   }

}
