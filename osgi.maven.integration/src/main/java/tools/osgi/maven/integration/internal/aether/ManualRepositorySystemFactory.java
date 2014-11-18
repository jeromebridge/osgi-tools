package tools.osgi.maven.integration.internal.aether;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

/**
* A factory for repository system instances that employs Aether's built-in service locator infrastructure to wire up
* the system's components.
*/
public class ManualRepositorySystemFactory
{
   public static RepositorySystem newRepositorySystem()
   {
      /*
      * Aether's components implement org.eclipse.aether.spi.locator.Service to ease manual wiring and using the
      * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
      * factories.
      */
      DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
      locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );
      locator.addService( TransporterFactory.class, FileTransporterFactory.class );
      locator.addService( TransporterFactory.class, HttpTransporterFactory.class );
      locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler() {
         @Override
         public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception ) {
            exception.printStackTrace();
         }
      } );
      return locator.getService( RepositorySystem.class );
   }
}
