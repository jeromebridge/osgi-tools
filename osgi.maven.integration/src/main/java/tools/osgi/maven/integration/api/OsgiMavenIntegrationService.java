package tools.osgi.maven.integration.api;

import hudson.maven.MavenEmbedder;
import hudson.maven.MavenRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.felix.bundlerepository.Reason;
import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resolver;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.service.command.Descriptor;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.osgi.maven.integration.internal.MavenProjectHolder;
import tools.osgi.maven.integration.internal.MavenProjectsObrResult;
import tools.osgi.maven.integration.internal.ObrUtils;
import tools.osgi.maven.integration.internal.aether.Booter;

public class OsgiMavenIntegrationService {
   private static final Logger LOG = LoggerFactory.getLogger( OsgiMavenIntegrationService.class );

   BundleContext bundleContext;

   public OsgiMavenIntegrationService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   private List<File> getMavenProjectFolders( File folder ) {
      final List<File> result = new ArrayList<File>();
      for( File subFolder : folder.listFiles() ) {
         if( isMavenProject( subFolder ) ) {
            result.add( subFolder );
         }
      }
      return result;
   }

   private boolean isMavenProject( File folder ) {
      boolean result = folder.exists();
      result = result && folder.isDirectory();
      result = result && new File( folder.getAbsolutePath() + File.separator + "pom.xml" ).exists();
      return result;
   }

   private RepositoryAdmin getRepositoryAdmin() {
      return getOsgiService( bundleContext, RepositoryAdmin.class );
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private <T> T getOsgiService( BundleContext context, Class<T> clazz ) {
      final ServiceReference ref = context.getServiceReference( clazz.getName() );
      T result = null;
      if( ref != null ) {
         result = ( T )context.getService( ref );
      }
      return result;
   }

   @Descriptor("Analyzes the state of the OSGi container")
   public void deploy(
         @Descriptor("Path to workspace directory that contains Maven projects to deploy") String workspacePath
         ) {
      try {
         // Validate Workspace Exists
         final File workspaceFolder = new File( workspacePath );
         if( !workspaceFolder.exists() ) {
            throw new RuntimeException( String.format( "Workspace: %s could not be found", workspacePath ) );
         }

         // Validate RepositoryAdmin Service Is Available
         final RepositoryAdmin repositoryAdmin = getRepositoryAdmin();
         if( repositoryAdmin == null ) {
            throw new RuntimeException( "No Repository Admin service running" );
         }

         // Create Repository
         final List<String> projectFilter = new ArrayList<String>();
         final List<MavenProjectHolder> mavenProjects = getMavenProjects( workspaceFolder, projectFilter );
         final MavenProjectsObrResult obrResult = createObrResouresForMavenProjects( mavenProjects );
         final Resource[] resources = obrResult.getResources().toArray( new Resource[obrResult.getResources().size()] );
         final Repository repository = repositoryAdmin.getHelper().repository( resources );
         addRepository( repositoryAdmin, repository );

         // Deploy
         final Resolver resolver = repositoryAdmin.resolver();
         for( Resource mavenProjectResource : obrResult.getMavenProjectResources() ) {
            resolver.add( mavenProjectResource );
         }
         if( resolver.resolve() ) {
            resolver.deploy( Resolver.START );
         }
         else {
            final Reason[] reqs = resolver.getUnsatisfiedRequirements();
            for( int i = 0; i < reqs.length; i++ ) {
               System.out.println( "Unable to resolve: " + reqs[i].getRequirement() );
            }
         }

      }
      catch( Throwable exception ) {
         exception.printStackTrace();
      }
   }

   private File addRepository( RepositoryAdmin repositoryAdmin, Repository repository ) {
      try {
         final File repositoryFile = File.createTempFile( "osgi-maven-integration", ".xml" );
         final Writer writer = new FileWriter( repositoryFile );
         try {
            repositoryAdmin.getHelper().writeRepository( repository, writer );
         }
         finally {
            writer.close();
         }
         repositoryAdmin.addRepository( repositoryFile.toURI().toURL() );
         return repositoryFile;
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Error adding repository through Repository Admin service.", exception );
      }
   }

   private boolean isOsgiBundle( MavenProject project ) {
      final File classesFolder = new File( project.getBuild().getOutputDirectory() );
      return ObrUtils.isOsgiBundle( classesFolder );
   }

   private MavenProjectsObrResult createObrResouresForMavenProjects( List<MavenProjectHolder> mavenProjects ) {
      final MavenProjectsObrResult result = new MavenProjectsObrResult( mavenProjects );
      addAssemblyResources( result );
      addDependencyResources( result );
      return result;
   }

   private void addDependencyResources( MavenProjectsObrResult result ) {
      for( MavenProjectHolder holder : result.getMavenProjects() ) {
         final RepositorySystem system = Booter.newRepositorySystem();
         final RepositorySystemSession session = Booter.newRepositorySystemSession( system, new LocalRepository( holder.getEmbedder().getLocalRepositoryPath() ) );
         for( Dependency dependency : holder.getProject().getDependencies() ) {
            try {
               org.eclipse.aether.artifact.Artifact filter = new org.eclipse.aether.artifact.DefaultArtifact( dependency.getGroupId(), dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion() );
               final DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter( JavaScopes.RUNTIME );
               final CollectRequest collectRequest = new CollectRequest();
               collectRequest.setRoot( new org.eclipse.aether.graph.Dependency( filter, JavaScopes.RUNTIME ) );
               collectRequest.setRepositories( Booter.newRepositories( system, session ) );
               for( ArtifactRepository remoteRepository : holder.getProject().getRemoteArtifactRepositories() ) {
                  collectRequest.addRepository( new RemoteRepository.Builder( remoteRepository.getId(), "default", remoteRepository.getUrl() ).build() );
               }
               final DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, classpathFlter );
               final List<ArtifactResult> artifactResults = system.resolveDependencies( session, dependencyRequest ).getArtifactResults();
               for( ArtifactResult artifactResult : artifactResults ) {
                  if( !containsArtifact( result.getMavenProjects(), artifactResult ) ) {
                     final Resource resource = addJarResource( result, holder, artifactResult.getArtifact().getFile() );
                     if( resource == null ) {
                        LOG.warn( String.format( "Unable to add dependency: %s to OBR", dependency ) );
                        result.addDependencyNotAdded( holder, artifactResult.getArtifact().getFile() );
                     }
                     else {
                        LOG.info( String.format( "Add dependency: %s to OBR", dependency ) );
                        result.addDependencyAdded( holder, artifactResult.getArtifact().getFile() );
                     }
                  }
               }
            }
            catch( Throwable exception ) {
               throw new RuntimeException( String.format( "Error resolving dependency: %s", dependency ), exception );
            }
         }
      }
   }

   private void addAssemblyResources( MavenProjectsObrResult result ) {
      for( MavenProjectHolder holder : result.getMavenProjects() ) {
         if( isOsgiBundle( holder.getProject() ) ) {
            final File classesFolder = new File( holder.getProject().getBuild().getOutputDirectory() );
            final Resource projectResource = addAssemblyResource( result, holder, classesFolder.getAbsolutePath() );
            if( projectResource == null ) {
               LOG.warn( String.format( "Unable to add project: %s to OBR", classesFolder ) );
               result.addDependencyNotAdded( holder, classesFolder );
            }
            else {
               LOG.info( String.format( "Add project: %s to OBR", classesFolder ) );
               result.addDependencyAdded( holder, classesFolder );
            }
         }
      }
   }

   private List<MavenProjectHolder> getMavenProjects( File workspaceFolder, List<String> projectFilter ) {
      try {
         final List<MavenProjectHolder> result = new ArrayList<MavenProjectHolder>();
         final List<File> mavenProjectFolders = getMavenProjectFolders( workspaceFolder );
         for( File mavenProjectFolder : mavenProjectFolders ) {
            final File pomFile = new File( mavenProjectFolder.getAbsolutePath() + File.separator + "pom.xml" );
            final MavenRequest mavenRequest = new MavenRequest();
            mavenRequest.setPom( pomFile.getAbsolutePath() );
            final ClassLoader classLoader = bundleContext.getBundle().adapt( BundleWiring.class ).getClassLoader();
            final MavenEmbedder mavenEmbedder = new MavenEmbedder( classLoader, mavenRequest );
            final MavenProject project = mavenEmbedder.readProject( pomFile );
            if( isOsgiBundle( project ) ) {
               if( projectFilter == null || projectFilter.isEmpty() || projectFilter.contains( project.getArtifactId() ) ) {
                  result.add( new MavenProjectHolder( mavenEmbedder, project ) );
               }
            }
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting Maven projects for workspace: %s", workspaceFolder ), exception );
      }
   }

   private Resource addAssemblyResource( MavenProjectsObrResult result, MavenProjectHolder holder, String bundleFolder ) {
      try {
         final File resourceFile = new File( bundleFolder );
         final URI resourceUri = new URI( String.format( "assembly:%s", bundleFolder ) );
         final Resource resource = ObrUtils.createResource( getRepositoryAdmin().getHelper(), resourceFile, resourceUri );
         if( resource != null ) {
            result.addMavenProjectResource( holder, resource );
         }
         return resource;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error adding bundle resource: %s", bundleFolder ), exception );
      }
   }

   @SuppressWarnings("deprecation")
   private Resource addJarResource( MavenProjectsObrResult result, MavenProjectHolder holder, File jarFile ) {
      Resource resource = null;
      try {
         resource = getRepositoryAdmin().getHelper().createResource( jarFile.toURL() );
         if( resource != null ) {
            result.addResource( holder, resource );
         }
         return resource;
      }
      catch( Exception exception ) {
         LOG.warn( String.format( "Error adding jar resource: %s", jarFile ), exception );
      }
      return resource;
   }

   private boolean containsArtifact( List<MavenProjectHolder> mavenProjects, ArtifactResult artifactResult ) {
      boolean result = false;
      for( MavenProjectHolder holder : mavenProjects ) {
         if( holder.getProject().getArtifactId().equals( artifactResult.getArtifact().getArtifactId() ) ) {
            result = true;
            break;
         }
      }
      return result;
   }

}
