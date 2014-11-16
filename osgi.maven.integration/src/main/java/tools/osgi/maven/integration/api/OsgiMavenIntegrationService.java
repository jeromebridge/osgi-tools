package tools.osgi.maven.integration.api;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.osgi.framework.BundleContext;

public class OsgiMavenIntegrationService {

   BundleContext bundleContext;

   public OsgiMavenIntegrationService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   @Descriptor("Analyzes the state of the OSGi container")
   public void deploy(
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
      System.out.println( "HELLO" );
      final DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
      locator.addService( TransporterFactory.class, FileTransporterFactory.class );
      locator.addService( TransporterFactory.class, HttpTransporterFactory.class );
      locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler() {
         @Override
         public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception ) {
            exception.printStackTrace();
         }
      } );
      locator.getService( RepositorySystem.class );
   }
}
