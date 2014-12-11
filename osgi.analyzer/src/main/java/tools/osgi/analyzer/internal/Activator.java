package tools.osgi.analyzer.internal;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.util.tracker.ServiceTracker;

import tools.osgi.analyzer.api.IOsgiAnalyzerService;
import tools.osgi.analyzer.api.OsgiAnalyzerCommandService;

public class Activator implements BundleActivator {
   private InternalOsgiAnalyzerService analyzerService;

   public static class ConsoleLogImpl implements LogListener
   {
      public void logged( LogEntry log )
      {
         if( log.getMessage() != null )
            System.out.println( "[" + log.getBundle().getSymbolicName() + "] " + log.getMessage() );

      }
   }

   private ConsoleLogImpl m_console = new ConsoleLogImpl();
   private LinkedList<LogReaderService> m_readers = new LinkedList<LogReaderService>();

   private ServiceListener m_servlistener = new ServiceListener() {
      public void serviceChanged( ServiceEvent event )
      {
         BundleContext bc = event.getServiceReference().getBundle().getBundleContext();
         LogReaderService lrs = ( LogReaderService )bc.getService( event.getServiceReference() );
         if( lrs != null )
         {
            if( event.getType() == ServiceEvent.REGISTERED )
            {
               m_readers.add( lrs );
               lrs.addLogListener( m_console );
            }
            else if( event.getType() == ServiceEvent.UNREGISTERING )
            {
               lrs.removeLogListener( m_console );
               m_readers.remove( lrs );
            }
         }
      }
   };

   @Override
   public void start( BundleContext context ) throws Exception {
      try {
         final Hashtable<String, Object> props = new Hashtable<String, Object>();
         props.put( "osgi.command.scope", "tools" );
         props.put( "osgi.command.function", new String[]{ "analyze", "diagnose", "diagnose_class" } );
         context.registerService( OsgiAnalyzerCommandService.class.getName(), new OsgiAnalyzerCommandService( context ), props );

         analyzerService = new InternalOsgiAnalyzerService( context );
         analyzerService.start();

         context.registerService( IOsgiAnalyzerService.class.getName(), analyzerService, new Hashtable<String, Object>() );

         // LOGGING
         ServiceTracker logReaderTracker = new ServiceTracker( context, org.osgi.service.log.LogReaderService.class.getName(), null );
         logReaderTracker.open();
         Object[] readers = logReaderTracker.getServices();
         if( readers != null )
         {
            for( int i = 0; i < readers.length; i++ )
            {
               LogReaderService lrs = ( LogReaderService )readers[i];
               m_readers.add( lrs );
               lrs.addLogListener( m_console );
            }
         }

         logReaderTracker.close();

         // Add the ServiceListener, but with a filter so that we only receive events related to LogReaderService
         String filter = "(objectclass=" + LogReaderService.class.getName() + ")";
         try {
            context.addServiceListener( m_servlistener, filter );
         }
         catch( InvalidSyntaxException e ) {
            e.printStackTrace();
         }

      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error starting bundle: %s, Error: %s", context.getBundle().getSymbolicName(), exception.getMessage() ), exception );
      }
   }

   @Override
   public void stop( BundleContext context ) throws Exception {
      analyzerService.stop();

      for( Iterator<LogReaderService> i = m_readers.iterator(); i.hasNext(); )
      {
         LogReaderService lrs = i.next();
         lrs.removeLogListener( m_console );
         i.remove();
      }
   }

}
