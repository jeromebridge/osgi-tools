package tools.osgi.maven.integration.api;

import hudson.maven.MavenEmbedder;
import hudson.maven.MavenRequest;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.felix.bundlerepository.Repository;
import org.apache.felix.bundlerepository.RepositoryAdmin;
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
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.osgi.maven.integration.internal.MavenProjectHolder;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.BundleImportRequirement;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsObrResult;
import tools.osgi.maven.integration.internal.ObrUtils;
import tools.osgi.maven.integration.internal.aether.Booter;

public class OsgiMavenIntegrationService {
   private static final Logger LOG = LoggerFactory.getLogger( OsgiMavenIntegrationService.class );

   private BundleContext bundleContext;

   public OsgiMavenIntegrationService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   @Descriptor("Analyzes the state of the OSGi container")
   public void deploy(
         @Descriptor("Path to workspace directory that contains Maven projects to deploy") String workspacePath
         ) {
      try {
         final List<String> projectFilter = new ArrayList<String>();
         projectFilter.add( "yaas-commons" );
         projectFilter.add( "yaas-xml" );
         projectFilter.add( "yaas-db" );

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

         // Load Maven Projects
         final List<MavenProjectHolder> mavenProjects = getMavenProjects( workspaceFolder, projectFilter );
         final MavenProjectsBundleDeploymentPlan deploymentPlan = new MavenProjectsBundleDeploymentPlan( bundleContext, mavenProjects );
         printDeploymentPlan( deploymentPlan );

         // Install Maven Project Bundles
         final List<URI> mavenProjectBundleUris = getMavenProjectBundleUris( mavenProjects );
         final List<Bundle> projectBundles = new ArrayList<Bundle>();
         for( URI uri : mavenProjectBundleUris ) {
            try {
               final Bundle bundle = bundleContext.installBundle( uri.toURL().toExternalForm() );
               projectBundles.add( bundle );
               System.out.println( String.format( "Installed Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ) );
            }
            catch( Exception exception ) {
               System.out.println( "Failed to install: " + uri + " Reason: " + exception.getMessage() );
            }
         }

         final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
         if( !fw.resolveBundles( projectBundles ) ) {
            System.out.println( "Maven Projects Not Resolved" );

         }

         // Create Repository
         // final MavenProjectsObrResult obrResult = createObrResouresForMavenProjects( mavenProjects );

         // printObrSummary( obrResult );
         //         final Resource[] resources = obrResult.getResources().toArray( new Resource[obrResult.getResources().size()] );
         // final Repository repository = repositoryAdmin.getHelper().repository( resources );
         // final File repositoryFile = addRepository( repositoryAdmin, repository );

         // Determine If Each Maven Project Is Installed
         // Create Deployment Plan

         // Deploy
         //         final Resolver resolver = repositoryAdmin.resolver();
         //         for( Resource mavenProjectResource : obrResult.getMavenProjectResources() ) {
         //            resolver.add( mavenProjectResource );
         //         }
         //         resolver.resolve();
         // final Reason[] reqs = resolver.getUnsatisfiedRequirements();
         //         for( int i = 0; i < reqs.length; i++ ) {
         //            System.out.println( "Unable to resolve resource: " + reqs[i].getResource() + " requirement: " + reqs[i].getRequirement() );
         //         }
         // resolver.deploy( Resolver.START );

         // Check If Projects Installed

         //         // Remove Repository
         //         System.out.println( "Repository File: " + repositoryFile.toURI().toURL().toExternalForm() );
         //         repositoryAdmin.removeRepository( repositoryFile.toURI().toURL().toExternalForm() );
      }
      catch( Throwable exception ) {
         exception.printStackTrace();
      }
   }

   private void printDeploymentPlan( MavenProjectsBundleDeploymentPlan deploymentPlan ) {
      System.out.println( "Deployment Plan" );
      System.out.println( "===============================================" );
      for( MavenProjectBundleDeploymentPlan projectPlan : deploymentPlan.getProjectPlans() ) {
         System.out.println( String.format( "Maven Project: %s", projectPlan.getMavenProjectHolder().getProject().getArtifactId() ) );
         for( BundleImportRequirement importRequirement : projectPlan.getImportRequirements() ) {
            System.out.println( String.format( "   Import Requirement(%s): %s(%s)", importRequirement.getImportedPackage().getPackageName(), importRequirement.getResolveType().name(), importRequirement.getResolveDescription() ) );
         }
      }
   }

   private List<URI> getMavenProjectBundleUris( List<MavenProjectHolder> mavenProjects ) {
      final List<URI> result = new ArrayList<URI>();
      for( MavenProjectHolder holder : mavenProjects ) {
         result.add( getMavenProjectBundleUri( holder.getProject() ) );
      }
      return result;
   }

   private URI getMavenProjectBundleUri( MavenProject project ) {
      try {
         return new URI( String.format( "assembly:%s", getMavenProjectBundleFolder( project ).getAbsolutePath() ) );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting maven project bundle URI for: %s", project.getArtifactId() ), exception );
      }
   }

   private File getMavenProjectBundleFolder( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() );
   }

   private Resource addAssemblyResource( MavenProjectsObrResult result, MavenProjectHolder holder ) {
      try {
         final Resource resource = ObrUtils.createResource( getRepositoryAdmin().getHelper(), getMavenProjectBundleFolder( holder.getProject() ), getMavenProjectBundleUri( holder.getProject() ) );
         if( resource != null ) {
            result.addMavenProjectResource( holder, getMavenProjectBundleFolder( holder.getProject() ), resource );
         }
         return resource;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error adding bundle resource: %s", getMavenProjectBundleFolder( holder.getProject() ) ), exception );
      }
   }

   private void addAssemblyResources( MavenProjectsObrResult result ) {
      for( MavenProjectHolder holder : result.getMavenProjects() ) {
         if( isOsgiBundle( holder.getProject() ) ) {
            final File classesFolder = new File( holder.getProject().getBuild().getOutputDirectory() );
            final Resource projectResource = addAssemblyResource( result, holder );
            if( projectResource == null ) {
               LOG.warn( String.format( "Unable to add project: %s to OBR", classesFolder ) );
            }
            else {
               LOG.info( String.format( "Add project: %s to OBR", classesFolder ) );
            }
         }
      }
   }

   private void addDependencyResources( MavenProjectsObrResult result ) {
      for( MavenProjectHolder holder : result.getMavenProjects() ) {
         System.out.println( String.format( "Resolving Project: %s", holder.getProject().getArtifactId() ) );

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
                     addJarDependency( result, holder, artifactResult.getArtifact().getFile() );
                  }
               }
            }
            catch( Throwable exception ) {
               throw new RuntimeException( String.format( "Error resolving dependency: %s", dependency ), exception );
            }
         }
      }
   }

   private Resource addJarDependency( MavenProjectsObrResult result, MavenProjectHolder holder, File jarFile ) {
      Resource resource = null;
      try {
         resource = getRepositoryAdmin().getHelper().createResource( jarFile.toURI().toURL() );
         result.addMavenDependencyResource( holder, jarFile, resource );
         return resource;
      }
      catch( Exception exception ) {
         LOG.warn( String.format( "Error adding jar resource: %s", jarFile ), exception );
      }
      return resource;
   }

   @SuppressWarnings("unused")
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

   @SuppressWarnings("unused")
   private MavenProjectsObrResult createObrResouresForMavenProjects( List<MavenProjectHolder> mavenProjects ) {
      final MavenProjectsObrResult result = new MavenProjectsObrResult( mavenProjects );
      addAssemblyResources( result );
      addDependencyResources( result );
      return result;
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

   private List<MavenProjectHolder> getMavenProjects( File workspaceFolder, List<String> projectFilter ) {
      try {
         final List<MavenProjectHolder> result = new ArrayList<MavenProjectHolder>();
         final List<File> mavenProjectFolders = getMavenProjectFolders( workspaceFolder );
         for( File mavenProjectFolder : mavenProjectFolders ) {
            System.out.println( String.format( "Loading Project: %s", mavenProjectFolder ) );

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

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private <T> T getOsgiService( BundleContext context, Class<T> clazz ) {
      final ServiceReference ref = context.getServiceReference( clazz.getName() );
      T result = null;
      if( ref != null ) {
         result = ( T )context.getService( ref );
      }
      return result;
   }

   private RepositoryAdmin getRepositoryAdmin() {
      return getOsgiService( bundleContext, RepositoryAdmin.class );
   }

   private boolean isMavenProject( File folder ) {
      boolean result = folder.exists();
      result = result && folder.isDirectory();
      result = result && new File( folder.getAbsolutePath() + File.separator + "pom.xml" ).exists();
      return result;
   }

   private boolean isOsgiBundle( MavenProject project ) {
      final File classesFolder = new File( project.getBuild().getOutputDirectory() );
      return ObrUtils.isOsgiBundle( classesFolder );
   }

   @SuppressWarnings("unused")
   private void printObrSummary( MavenProjectsObrResult obrResult ) {
      try {
         final List<File> dependenciesAdded = new ArrayList<File>( obrResult.getDependenciesAdded() );
         final List<File> dependenciesNotAdded = new ArrayList<File>( obrResult.getDependenciesNotAdded() );
         final List<URI> mavenProjectsAdded = obrResult.getMavenProjectsAdded();
         Collections.sort( dependenciesAdded );
         Collections.sort( dependenciesNotAdded );
         Collections.sort( mavenProjectsAdded );
         System.out.println( "Maven Projects" );
         System.out.println( "===============================================" );
         for( URI uri : mavenProjectsAdded ) {
            System.out.println( "   " + uri.toURL().toExternalForm() );
         }
         //         System.out.println( "Dependencies Added" );
         //         System.out.println( "===============================================" );
         //         for( File file : dependenciesAdded ) {
         //            System.out.println( "   " + file.toURI().toURL().toExternalForm() );
         //         }
         //         System.out.println( "" );
         //
         //         System.out.println( "Dependencies Not Added" );
         //         System.out.println( "===============================================" );
         //         for( File file : dependenciesNotAdded ) {
         //            System.out.println( "   " + file.toURI().toURL().toExternalForm() );
         //         }
         //         System.out.println( "" );
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Error printing OBR results", exception );
      }
   }

}
