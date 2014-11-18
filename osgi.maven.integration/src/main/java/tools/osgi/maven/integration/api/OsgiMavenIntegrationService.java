package tools.osgi.maven.integration.api;

import hudson.maven.MavenEmbedder;
import hudson.maven.MavenRequest;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.bundlerepository.RepositoryAdmin;
import org.apache.felix.bundlerepository.Resource;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
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
               absentValue = "false") boolean includeAll,
         @Descriptor("Bundle ID to diagnose issues") String workspacePath
         ) {
      try {
         final File workspaceFolder = new File( workspacePath );
         if( !workspaceFolder.exists() ) {
            throw new RuntimeException( String.format( "Workspace: %s could not be found", workspacePath ) );
         }
         final List<String> projectFilter = new ArrayList<String>();

         // Filter Maven Projects
         final List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
         final List<File> mavenProjectFolders = getMavenProjectFolders( workspaceFolder );
         for( File mavenProjectFolder : mavenProjectFolders ) {
            final File pomFile = new File( mavenProjectFolder.getAbsolutePath() + File.separator + "pom.xml" );
            final MavenRequest mavenRequest = new MavenRequest();
            mavenRequest.setPom( pomFile.getAbsolutePath() );
            final ClassLoader classLoader = bundleContext.getBundle().adapt( BundleWiring.class ).getClassLoader();
            final MavenEmbedder mavenEmbedder = new MavenEmbedder( classLoader, mavenRequest );
            final MavenProject project = mavenEmbedder.readProject( pomFile );
            if( projectFilter == null || projectFilter.isEmpty() || projectFilter.contains( project.getArtifactId() ) ) {
               mavenProjects.add( new MavenProjectHolder( mavenEmbedder, project ) );
            }
         }

         final Set<File> dependenciesAdded = new HashSet<File>();
         final Set<File> dependenciesNotAdded = new HashSet<File>();
         final List<Resource> resources = new ArrayList<Resource>();
         final RepositoryAdmin repositoryAdmin = getRepositoryAdmin();
         if( repositoryAdmin == null ) {
            throw new RuntimeException( "No Repository Admin service running" );
         }

         for( MavenProjectHolder holder : mavenProjects ) {
            // Assembly
            final File classesFolder = new File( holder.getProject().getBuild().getOutputDirectory() );
            if( ObrUtils.isOsgiBundle( classesFolder ) ) {
               final Resource projectResource = addAssemblyResource( resources, classesFolder.getAbsolutePath() );
               if( projectResource == null ) {
                  LOG.warn( String.format( "Unable to add project: %s to OBR", classesFolder ) );
                  dependenciesNotAdded.add( classesFolder );
               }
               else {
                  LOG.info( String.format( "Add project: %s to OBR", classesFolder ) );
                  dependenciesAdded.add( classesFolder );
               }

               // Dependencies
               final RepositorySystem system = Booter.newRepositorySystem();
               final RepositorySystemSession session = Booter.newRepositorySystemSession( system, new LocalRepository( holder.getEmbedder().getLocalRepositoryPath() ) );
               for( Dependency dependency : holder.getProject().getDependencies() ) {
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
                     if( !containsArtifact( mavenProjects, artifactResult ) ) {
                        final Resource resource = addJarResource( resources, artifactResult.getArtifact().getFile() );
                        if( resource == null ) {
                           LOG.warn( String.format( "Unable to add dependency: %s to OBR", dependency ) );
                           dependenciesNotAdded.add( artifactResult.getArtifact().getFile() );
                        }
                        else {
                           LOG.info( String.format( "Add dependency: %s to OBR", dependency ) );
                           dependenciesAdded.add( artifactResult.getArtifact().getFile() );
                        }
                     }
                  }
               }
            }
         }
      }
      catch( Throwable exception ) {
         exception.printStackTrace();
      }
   }

   private Resource addAssemblyResource( List<Resource> resources, String bundleFolder ) {
      try {
         final File resourceFile = new File( bundleFolder );
         final URI resourceUri = new URI( String.format( "assembly:%s", bundleFolder ) );
         final Resource resource = ObrUtils.createResource( getRepositoryAdmin().getHelper(), resourceFile, resourceUri );
         if( resource != null ) {
            resources.add( resource );
         }
         return resource;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error adding bundle resource: %s", bundleFolder ), exception );
      }
   }

   @SuppressWarnings("deprecation")
   private Resource addJarResource( List<Resource> resources, File jarFile ) {
      Resource resource = null;
      try {
         resource = getRepositoryAdmin().getHelper().createResource( jarFile.toURL() );
         if( resource != null ) {
            resources.add( resource );
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
