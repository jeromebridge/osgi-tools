package tools.osgi.maven.integration.api;

import hudson.maven.MavenEmbedder;
import hudson.maven.MavenRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.bundlerepository.Repository;
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
import org.eclipse.virgo.nano.deployer.api.core.ApplicationDeployer;
import org.eclipse.virgo.nano.deployer.api.core.DeploymentIdentity;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.service.packageadmin.PackageAdmin;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.osgi.analyzer.api.IOsgiAnalyzerService;
import tools.osgi.analyzer.api.UseConflict;
import tools.osgi.maven.integration.internal.Duration;
import tools.osgi.maven.integration.internal.MavenProjectHolder;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.AbstractBundleValidationError;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.BundleImportRequirement;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.MavenDependencyBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsObrResult;
import tools.osgi.maven.integration.internal.ObrUtils;
import tools.osgi.maven.integration.internal.aether.Booter;

import com.springsource.util.osgi.manifest.Resolution;

@SuppressWarnings("deprecation")
public class OsgiMavenIntegrationService {
   private static final Logger LOG = LoggerFactory.getLogger( OsgiMavenIntegrationService.class );

   private BundleContext bundleContext;

   public OsgiMavenIntegrationService( BundleContext bundleContext ) {
      this.bundleContext = bundleContext;
   }

   // install -start assembly:/home/developer/git/osgi-tools/osgi.analyzer/bin/maven/classes
   // install -start assembly:/home/developer/git/osgi-tools/osgi.maven.integration/bin/maven/classes

   //   @Descriptor("Test Code")
   //   public void deploy() throws Exception {
   //
   //      final ApplicationDeployer deployer = getApplicationDeployer();
   //      System.out.println( "Deployer Service: " + deployer );
   //      // deployer.deploy( new URI( "" ) );
   //
   //      final File jarFile = File.createTempFile( "temp", ".jar" );
   //      final FileOutputStream fos = new FileOutputStream( jarFile );
   //      System.out.println( "Jar File: " + jarFile.toURI().toURL().toExternalForm() );
   //
   //      final JarBuilder builder = new JarBuilder();
   //      builder.add( new File( "/home/developer/git/yet-another-admin-system/yaas-ws/bin/maven/classes" ) );
   //      builder.build( fos );
   //
   //      deployer.deploy( jarFile.toURI() );
   //   }

   @Descriptor("Deploys all the subfolders of the specified directory if they are compiled Maven projects that are also bundles.")
   public void deploy(
         @Descriptor("Print verbose messages") @Parameter(
               names = { "-v", "--verbose" },
               presentValue = "true",
               absentValue = "false") boolean verbose,
         @Descriptor("Print deployment plan only") @Parameter(
               names = { "-po", "--plan-only" },
               presentValue = "true",
               absentValue = "false") boolean planOnly,
         @Descriptor("Uninstall/Install existing project bundles") @Parameter(
               names = { "-r", "--reinstall" },
               presentValue = "true",
               absentValue = "false") boolean reinstall,
         @Descriptor("Refresh Use Conflict bundles") @Parameter(
               names = { "-ru", "--refresh-use-conflicts" },
               presentValue = "true",
               absentValue = "false") boolean refreshUseConflicts,
         @Descriptor("Dependencies Only") @Parameter(
               names = { "-do", "--dependencies-only" },
               presentValue = "true",
               absentValue = "false") boolean dependenciesOnly,
         @Descriptor("Show Optional Imports (Unresolved)") @Parameter(
               names = { "-oi", "--show-optional-imports" },
               presentValue = "true",
               absentValue = "false") boolean showOptionalImports,
         @Descriptor("Path to workspace directory that contains Maven projects to deploy") String workspacePath
         ) {
      deploy( verbose, planOnly, reinstall, refreshUseConflicts, dependenciesOnly, showOptionalImports, workspacePath, null );
   }

   @Descriptor("Deploys all the subfolders of the specified directory if they are compiled Maven projects that are also bundles.")
   public void deploy(
         @Descriptor("Print verbose messages") @Parameter(
               names = { "-v", "--verbose" },
               presentValue = "true",
               absentValue = "false") boolean verbose,
         @Descriptor("Print deployment plan only") @Parameter(
               names = { "-po", "--plan-only" },
               presentValue = "true",
               absentValue = "false") boolean planOnly,
         @Descriptor("Uninstall/Install existing project bundles") @Parameter(
               names = { "-r", "--reinstall" },
               presentValue = "true",
               absentValue = "false") boolean reinstall,
         @Descriptor("Refresh Use Conflict bundles") @Parameter(
               names = { "-ru", "--refresh-use-conflicts" },
               presentValue = "true",
               absentValue = "false") boolean refreshUseConflicts,
         @Descriptor("Dependencies Only") @Parameter(
               names = { "-do", "--dependencies-only" },
               presentValue = "true",
               absentValue = "false") boolean dependenciesOnly,
         @Descriptor("Show Optional Imports (Unresolved)") @Parameter(
               names = { "-oi", "--show-optional-imports" },
               presentValue = "true",
               absentValue = "false") boolean showOptionalImports,
         @Descriptor("Path to workspace directory that contains Maven projects to deploy") String workspacePath,
         @Descriptor("List of projects to include from the workspace") String[] includeProjects
         ) {
      try {
         final Date startTime = new Date();

         // Validate Workspace Exists
         final File workspaceFolder = new File( workspacePath );
         if( !workspaceFolder.exists() ) {
            throw new RuntimeException( String.format( "Workspace: %s could not be found", workspacePath ) );
         }

         // Project Filters
         final List<String> projectFilter = new ArrayList<String>();
         if( includeProjects != null ) {
            projectFilter.addAll( Arrays.asList( includeProjects ) );
         }

         // Validate RepositoryAdmin Service Is Available
         final RepositoryAdmin repositoryAdmin = getRepositoryAdmin();
         if( repositoryAdmin == null ) {
            throw new RuntimeException( "No Repository Admin service running" );
         }

         // Load Maven Projects
         final List<MavenProjectHolder> mavenProjects = getMavenProjects( workspaceFolder, projectFilter );
         final MavenProjectsBundleDeploymentPlan deploymentPlan = new MavenProjectsBundleDeploymentPlan( bundleContext, mavenProjects );
         printDeploymentPlan( deploymentPlan, showOptionalImports, verbose );

         // Plan Only
         if( planOnly ) {
            printSummary( startTime, deploymentPlan );
            return;
         }

         // Stop If Validation Errors
         if( deploymentPlan.isValidationErrors() ) {
            printSummary( startTime, deploymentPlan );
            return;
         }

         // Uninstall First?
         if( reinstall ) {
            for( AbstractBundleDeploymentPlan plan : deploymentPlan.getProjectPlans() ) {
               final Bundle existing = getExisting( plan );
               if( existing != null ) {
                  existing.uninstall();
                  System.out.println( String.format( "Uninstalled Bundle(%s): %s", existing.getBundleId(), existing.getSymbolicName() ) );
               }
            }

            // Removal Pending
            final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
            for( Bundle removalPending : fw.getRemovalPendingBundles() ) {
               // System.out.println( String.format( "Removal Pending: %s(%s)", removalPending.getSymbolicName(), removalPending.getBundleId() ) );
               fw.refreshBundles( Arrays.asList( removalPending ) );
            }
         }

         // Stop Existing Project Bundles
         for( AbstractBundleDeploymentPlan plan : deploymentPlan.getProjectPlans() ) {
            final Bundle existing = getExisting( plan );
            if( existing != null ) {
               existing.stop();
               System.out.println( String.format( "Stopped Bundle(%s): %s", existing.getBundleId(), existing.getSymbolicName() ) );
            }
         }

         // Install Plan
         final List<Bundle> installedBundles = new ArrayList<Bundle>();
         if( deploymentPlan.isResolved( Resolution.MANDATORY ) ) {
            for( AbstractBundleDeploymentPlan plan : deploymentPlan.getInstallOrder() ) {
               if( !dependenciesOnly || plan instanceof MavenDependencyBundleDeploymentPlan ) {
                  try {
                     final Bundle existing = getExisting( plan );
                     if( existing != null ) {
                        update( plan );
                        installedBundles.add( existing );
                        System.out.println( String.format( "Updated Bundle(%s): %s", existing.getBundleId(), existing.getSymbolicName() ) );
                     }
                     else {
                        final Bundle bundle = install( plan );
                        installedBundles.add( bundle );
                        System.out.println( String.format( "Installed Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ) );
                     }
                  }
                  catch( Exception exception ) {
                     System.out.println( "Failed to install: " + plan + " Reason: " + exception.getMessage() );
                     throw new RuntimeException( "Failed to install: " + plan, exception );
                  }
               }
            }
         }

         // Resolve And Start
         final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
         if( !fw.resolveBundles( installedBundles ) ) {
            System.out.println( "Maven Projects Not Resolved" );
            final PackageAdmin packageAdmin = getPackageAdmin();
            final Bundle[] refreshBundles = new Bundle[installedBundles.size()];
            installedBundles.toArray( refreshBundles );
            packageAdmin.refreshPackages( refreshBundles );
         }
         for( Bundle bundle : installedBundles ) {
            try {
               bundle.start();
            }
            catch( Exception exception ) {
               if( refreshUseConflicts && isUseConflict( bundle ) ) {
                  try {
                     refreshBundleWithUseConflicts( bundle );
                     bundle.start();
                  }
                  catch( Exception exception2 ) {
                     // Diagnose Exception
                     getOsgiAnalyzerService().diagnose( exception2 );
                  }
               }
               else {
                  System.out.println( String.format( "Failed Starting Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ) );
                  exception.printStackTrace();
                  throw new RuntimeException( String.format( "Error Starting Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ), exception );
               }
            }
            System.out.println( String.format( "Started Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ) );
         }

         // Print Durations
         printSummary( startTime, deploymentPlan );
      }
      catch( Throwable exception ) {
         exception.printStackTrace();
      }
   }

   private void printSummary( Date startTime, MavenProjectsBundleDeploymentPlan deploymentPlan ) {
      System.out.println( "" );
      System.out.println( "" );
      System.out.println( "===============================================" );
      System.out.println( "Deploy Summary" );
      System.out.println( "===============================================" );
      printDeploymentPlanDurations( deploymentPlan );
      System.out.println( "-----------------------------------------------" );
      printDuration( new Duration( startTime, new Date() ), "Total Deploy Time" );
      System.out.println( "" );
   }

   private void printDeploymentPlanDurations( MavenProjectsBundleDeploymentPlan deploymentPlan ) {
      printDuration( deploymentPlan.getInitDependencyPlansDuration(), "Init Dependency Plans" );
      printDuration( deploymentPlan.getInitProjectPlansDuration(), "Init Project Plans" );
      printDuration( deploymentPlan.getInitInstallOrderDuration(), "Init Install Order" );
   }

   private void printDuration( Duration duration, String description ) {
      if( duration != null ) {
         System.out.println( duration.getFormatted( description ) );
      }
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

   private ApplicationDeployer getApplicationDeployer() {
      final ServiceTracker<ApplicationDeployer, Object> packageAdminTracker = new ServiceTracker<ApplicationDeployer, Object>( bundleContext, ApplicationDeployer.class.getName(), null );
      packageAdminTracker.open();
      final ApplicationDeployer result = ( ApplicationDeployer )packageAdminTracker.getService();
      return result;
   }

   private Bundle getBundleByNameOrId( String bundleId ) {
      Bundle result = null;
      if( isLong( bundleId ) ) {
         result = bundleContext.getBundle( Long.valueOf( bundleId ) );
      }
      else {
         for( Bundle bundle : bundleContext.getBundles() ) {
            if( bundle.getSymbolicName().equals( bundleId ) ) {
               result = bundle;
               break;
            }
         }
      }
      return result;
   }

   private Bundle getExisting( AbstractBundleDeploymentPlan plan ) {
      try {
         Bundle result = null;
         if( isVirgoEnvironment() && plan.isWebBundle() ) {
            result = getBundleByNameOrId( plan.getManifest().getBundleSymbolicName().getSymbolicName() );
         }
         else {
            result = bundleContext.getBundle( plan.getBundleUri().toURL().toExternalForm() );
         }
         return result;
      }
      catch( Throwable exception ) {
         throw new RuntimeException( "Error finding existing bundle for plan: " + plan, exception );
      }
   }

   private File getMavenProjectBundleFolder( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() );
   }

   private URI getMavenProjectBundleUri( MavenProject project ) {
      try {
         return new URI( String.format( "assembly:%s", getMavenProjectBundleFolder( project ).getAbsolutePath() ) );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting maven project bundle URI for: %s", project.getArtifactId() ), exception );
      }
   }

   @SuppressWarnings("unused")
   private List<URI> getMavenProjectBundleUris( List<MavenProjectHolder> mavenProjects ) {
      final List<URI> result = new ArrayList<URI>();
      for( MavenProjectHolder holder : mavenProjects ) {
         result.add( getMavenProjectBundleUri( holder.getProject() ) );
      }
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

   private IOsgiAnalyzerService getOsgiAnalyzerService() {
      final ServiceTracker<IOsgiAnalyzerService, Object> tracker = new ServiceTracker<IOsgiAnalyzerService, Object>( bundleContext, IOsgiAnalyzerService.class.getName(), null );
      tracker.open();
      final IOsgiAnalyzerService result = ( IOsgiAnalyzerService )tracker.getService();
      return result;
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

   private PackageAdmin getPackageAdmin() {
      final ServiceTracker<PackageAdmin, Object> packageAdminTracker = new ServiceTracker<PackageAdmin, Object>( bundleContext, PackageAdmin.class.getName(), null );
      packageAdminTracker.open();
      final PackageAdmin packageAdmin = ( PackageAdmin )packageAdminTracker.getService();
      return packageAdmin;
   }

   private RepositoryAdmin getRepositoryAdmin() {
      return getOsgiService( bundleContext, RepositoryAdmin.class );
   }

   private Bundle install( AbstractBundleDeploymentPlan plan ) {
      try {
         Bundle result = null;
         if( isVirgoEnvironment() && plan.isWebBundle() ) {
            if( plan.getFile().isDirectory() ) {
               final File jarFile = plan.createJarFile();
               final DeploymentIdentity id = getApplicationDeployer().deploy( jarFile.toURI() );
               result = getBundleByNameOrId( id.getSymbolicName() );
            }
            else {
               final DeploymentIdentity id = getApplicationDeployer().deploy( plan.getFile().toURI() );
               result = getBundleByNameOrId( id.getSymbolicName() );
            }
         }
         else {
            result = bundleContext.installBundle( plan.getBundleUri().toURL().toExternalForm() );
         }
         return result;
      }
      catch( Throwable exception ) {
         throw new RuntimeException( "Error deploying plan: " + plan, exception );
      }
   }

   private boolean isLong( String value ) {
      boolean result = true;
      try {
         Long.parseLong( value );
      }
      catch( Throwable exception ) {
         result = false;
      }
      return result;
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

   private boolean isUseConflict( Bundle bundle ) {
      return !getOsgiAnalyzerService().findUseConflicts( bundle ).isEmpty();
   }

   private boolean isVirgoEnvironment() {
      // TODO Determine If Virgo Environment
      return true;
   }

   private void printDeploymentPlan( MavenProjectsBundleDeploymentPlan deploymentPlan, boolean showOptionalImports, boolean verbose ) {
      System.out.println( "Deployment Plan" );
      System.out.println( "===============================================" );
      for( AbstractBundleDeploymentPlan plan : deploymentPlan.getInstallOrder() ) {
         System.out.println( plan );
         if( verbose ) {
            for( BundleImportRequirement importRequirement : plan.getImportRequirements() ) {
               System.out.println( "   " + importRequirement );
            }
            for( MavenProjectBundleDeploymentPlan dependent : deploymentPlan.getDependentMavenProjectBundleDeploymentPlans( plan ) ) {
               System.out.println( "   Maven Project Reference: " + dependent );
            }
         }
      }
      System.out.println( "" );
      printUnresolved( deploymentPlan, verbose, Resolution.MANDATORY );
      if( showOptionalImports ) {
         printUnresolved( deploymentPlan, verbose, Resolution.OPTIONAL );
      }
      System.out.println( "" );
      printValidationErrors( deploymentPlan, verbose );
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
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Error printing OBR results", exception );
      }
   }

   private void printUnresolved( MavenProjectsBundleDeploymentPlan deploymentPlan, boolean verbose, Resolution resolution ) {
      if( !deploymentPlan.isResolved( resolution ) ) {
         System.out.println( String.format( "Unresolved (%s)", resolution.name() ) );
         System.out.println( "===============================================" );
         for( AbstractBundleDeploymentPlan plan : deploymentPlan.getUnresolvedPlans( resolution ) ) {
            System.out.println( plan );
            for( BundleImportRequirement importRequirement : plan.getUnresolvedImportRequirements( resolution ) ) {
               System.out.println( "   " + importRequirement );
            }
            if( !( plan instanceof MavenProjectBundleDeploymentPlan ) ) {
               for( MavenProjectBundleDeploymentPlan dependent : deploymentPlan.getDependentMavenProjectBundleDeploymentPlans( plan ) ) {
                  System.out.println( "   Maven Project Reference: " + dependent );
               }
            }
         }
      }
   }

   private void printValidationErrors( MavenProjectsBundleDeploymentPlan deploymentPlan, boolean verbose ) {
      if( deploymentPlan.isValidationErrors() ) {
         System.out.println( "Validation Errors" );
         System.out.println( "===============================================" );
         for( AbstractBundleDeploymentPlan plan : deploymentPlan.getPlansWithValidationErrors() ) {
            System.out.println( plan );
            for( AbstractBundleValidationError error : plan.getValidationErrors() ) {
               System.out.println( "   " + error );
            }
            if( !( plan instanceof MavenProjectBundleDeploymentPlan ) ) {
               for( MavenProjectBundleDeploymentPlan dependent : deploymentPlan.getDependentMavenProjectBundleDeploymentPlans( plan ) ) {
                  System.out.println( "   Maven Project Reference: " + dependent );
               }
            }
         }
      }
   }

   private void refreshBundleWithUseConflicts( Bundle bundle ) {
      final Set<Bundle> bundles = new HashSet<Bundle>();
      bundles.add( bundle );
      for( UseConflict useConflict : getOsgiAnalyzerService().findUseConflicts( bundle ) ) {
         bundles.add( useConflict.getUseConflictBundle() );
      }
      final Bundle[] refreshBundles = new Bundle[bundles.size()];
      bundles.toArray( refreshBundles );
      getPackageAdmin().refreshPackages( refreshBundles );
   }

   private Bundle update( AbstractBundleDeploymentPlan plan ) {
      try {
         final Bundle bundle = getExisting( plan );
         if( bundle != null ) {
            if( isVirgoEnvironment() && plan.isWebBundle() ) {
               final File jarFile = plan.createJarFile();
               bundle.update( new FileInputStream( jarFile ) );
            }
            else {
               bundle.update();
            }
         }
         return bundle;
      }
      catch( Throwable exception ) {
         throw new RuntimeException( "Error updating existing bundle for plan: " + plan, exception );
      }
   }

}
