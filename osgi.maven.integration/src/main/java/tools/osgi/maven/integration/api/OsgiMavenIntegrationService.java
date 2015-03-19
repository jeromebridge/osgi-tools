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

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import org.eclipse.aether.artifact.Artifact;
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

import tools.osgi.analyzer.api.BundleUtils;
import tools.osgi.analyzer.api.IOsgiAnalyzerService;
import tools.osgi.analyzer.api.UsesConflict;
import tools.osgi.maven.integration.internal.Duration;
import tools.osgi.maven.integration.internal.FileUtils;
import tools.osgi.maven.integration.internal.MavenProjectHolder;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.AbstractBundleValidationError;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.BundleDependency;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.BundleImportRequirement;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.IBundleDeployment;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.MavenDependencyBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan;
import tools.osgi.maven.integration.internal.MavenProjectsObrResult;
import tools.osgi.maven.integration.internal.MavenUtils;
import tools.osgi.maven.integration.internal.ObrUtils;
import tools.osgi.maven.integration.internal.UsesConflictError;
import tools.osgi.maven.integration.internal.aether.Booter;

import com.springsource.util.osgi.manifest.Resolution;

@SuppressWarnings("deprecation")
public class OsgiMavenIntegrationService {
   private static final Logger LOG = LoggerFactory.getLogger( OsgiMavenIntegrationService.class );

   private BundleContext bundleContext;
   private List<DeployedMavenProject> deployedMavenProjects = new ArrayList<DeployedMavenProject>();

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
               absentValue = "false") boolean refreshUsesConflicts,
         @Descriptor("Dependencies Only") @Parameter(
               names = { "-do", "--dependencies-only" },
               presentValue = "true",
               absentValue = "false") boolean dependenciesOnly,
         @Descriptor("Include Dependencies In Deploy Plan") @Parameter(
               names = { "-id", "--include-dependencies" },
               presentValue = "true",
               absentValue = "false") boolean includeDependencies,
         @Descriptor("Show Optional Imports (Unresolved)") @Parameter(
               names = { "-oi", "--show-optional-imports" },
               presentValue = "true",
               absentValue = "false") boolean showOptionalImports,
         @Descriptor("Force projects to be reinstalled even if no changes") @Parameter(
               names = { "-f", "--force" },
               presentValue = "true",
               absentValue = "false") boolean force,
         @Descriptor("Attempt to diagnose errors that happen during deployment") @Parameter(
               names = { "-d", "--diagnose" },
               presentValue = "true",
               absentValue = "false") boolean diagnose,
         @Descriptor("Path to workspace directory that contains Maven projects to deploy") String workspacePath
         ) {
      deploy( verbose, planOnly, reinstall, refreshUsesConflicts, dependenciesOnly, includeDependencies, showOptionalImports, force, diagnose, workspacePath, null );
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
               absentValue = "false") boolean refreshUsesConflicts,
         @Descriptor("Dependencies Only") @Parameter(
               names = { "-do", "--dependencies-only" },
               presentValue = "true",
               absentValue = "false") boolean dependenciesOnly,
         @Descriptor("Include Dependencies In Deploy Plan") @Parameter(
               names = { "-id", "--include-dependencies" },
               presentValue = "true",
               absentValue = "false") boolean includeDependencies,
         @Descriptor("Show Optional Imports (Unresolved)") @Parameter(
               names = { "-oi", "--show-optional-imports" },
               presentValue = "true",
               absentValue = "false") boolean showOptionalImports,
         @Descriptor("Force projects to be reinstalled even if no changes") @Parameter(
               names = { "-f", "--force" },
               presentValue = "true",
               absentValue = "false") boolean force,
         @Descriptor("Attempt to diagnose errors that happen during deployment") @Parameter(
               names = { "-d", "--diagnose" },
               presentValue = "true",
               absentValue = "false") boolean diagnose,
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

         // Force
         if( force ) {
            deployedMavenProjects.clear();
         }

         // Load Maven Projects
         final List<MavenProjectHolder> mavenProjects = getMavenProjects( workspaceFolder, projectFilter );

         // Deployment Plan
         final MavenProjectsBundleDeploymentPlan deploymentPlan = new MavenProjectsBundleDeploymentPlan( bundleContext, mavenProjects, deployedMavenProjects, includeDependencies );
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
               final Bundle existing = plan.getExistingBundle();
               if( existing != null ) {
                  existing.uninstall();
                  System.out.println( String.format( "Uninstalled Bundle(%s): %s", existing.getBundleId(), existing.getSymbolicName() ) );
               }

               removeDeployed( existing );
            }

            // Clear Removal Pending
            final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
            for( Bundle removalPending : fw.getRemovalPendingBundles() ) {
               // System.out.println( String.format( "Removal Pending: %s(%s)", removalPending.getSymbolicName(), removalPending.getBundleId() ) );
               fw.refreshBundles( Arrays.asList( removalPending ) );
            }
         }

         // Stop Existing Project Bundles
         for( AbstractBundleDeploymentPlan plan : deploymentPlan.getProjectPlans() ) {
            if( plan.hasExistingBundle() ) {
               plan.getExistingBundle().stop();
               System.out.println( String.format( "Stopped Bundle(%s): %s", plan.getExistingBundle().getBundleId(), plan.getExistingBundle().getSymbolicName() ) );
            }
         }

         // Stop Existing Dependent Bundles
         for( BundleDependency dependency : deploymentPlan.getExistingBundleDependencies() ) {
            dependency.getExistingBundle().stop();
            System.out.println( String.format( "Stopped Bundle(%s): %s", dependency.getExistingBundle().getBundleId(), dependency.getExistingBundle().getSymbolicName() ) );
         }

         // Install Plan
         final List<Bundle> installedBundles = new ArrayList<Bundle>();
         if( deploymentPlan.isResolved( Resolution.MANDATORY ) ) {
            for( AbstractBundleDeploymentPlan plan : deploymentPlan.getInstallOrder() ) {
               if( !dependenciesOnly || plan instanceof MavenDependencyBundleDeploymentPlan ) {
                  try {
                     if( plan.hasExistingBundle() ) {
                        update( plan );
                        installedBundles.add( plan.getExistingBundle() );
                        System.out.println( String.format( "Updated Bundle(%s): %s", plan.getExistingBundle().getBundleId(), plan.getExistingBundle().getSymbolicName() ) );
                     }
                     else {
                        final Bundle bundle = install( plan );
                        installedBundles.add( bundle );
                        System.out.println( String.format( "Installed Bundle(%s): %s", bundle.getBundleId(), bundle.getSymbolicName() ) );
                     }
                  }
                  catch( Exception exception ) {
                     System.out.println( String.format( "Failed to install: %s Reason: %s", plan, ExceptionUtils.getRootCauseMessage( exception ) ) );
                     if( diagnose ) {
                        diagnoseInstallFailure( plan, exception );
                     }
                     throw new RuntimeException( "Failed to install: " + plan, exception );
                  }

                  // Append To Deployed List
                  addDeployed( plan, installedBundles.get( installedBundles.size() - 1 ) );
               }
            }
         }

         // Resolve Installed Bundles
         final FrameworkWiring fw = bundleContext.getBundle( 0 ).adapt( FrameworkWiring.class );
         if( !fw.resolveBundles( installedBundles ) ) {
            System.out.println( "Maven Projects Not Resolved" );
            final PackageAdmin packageAdmin = getPackageAdmin();
            final Bundle[] refreshBundles = new Bundle[installedBundles.size()];
            installedBundles.toArray( refreshBundles );
            packageAdmin.refreshPackages( refreshBundles );
         }

         // Refresh Dependent Bundles
         refreshDependentBundles( deploymentPlan );

         // Start Bundles
         for( IBundleDeployment deploy : deploymentPlan.getStartOrder() ) {
            final Bundle bundle = deploy.getExistingBundle();
            try {
               Validate.notNull( bundle, "No installed bundle could be found for: %s(%s)", bundle.getSymbolicName(), bundle.getBundleId() );
               bundle.start();
            }
            catch( Exception exception ) {
               if( refreshUsesConflicts && isUsesConflict( bundle ) ) {
                  try {
                     refreshBundleWithUsesConflicts( bundle );
                     bundle.start();
                  }
                  catch( Exception exception2 ) {
                     // Diagnose Exception
                     getOsgiAnalyzerService().diagnose( exception2 );
                  }
               }
               else {
                  System.out.println( String.format( "Failed Starting: %s(%s)", bundle.getSymbolicName(), bundle.getBundleId() ) );
                  exception.printStackTrace();
                  throw new RuntimeException( String.format( "Error Starting: %s(%s)", bundle.getSymbolicName(), bundle.getBundleId() ), exception );
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

   @SuppressWarnings("unused")
   private void refreshDependentBundles( MavenProjectsBundleDeploymentPlan deploymentPlan, IBundleDeployment deployed ) {
      for( BundleDependency dependency : deploymentPlan.getExistingBundleDependencies( deployed ) ) {
         getPackageAdmin().refreshPackages( new Bundle[]{ dependency.getExistingBundle() } );
         System.out.println( String.format( "Refreshed Bundle(%s): %s", dependency.getExistingBundle().getBundleId(), dependency.getExistingBundle().getSymbolicName() ) );
      }
   }

   private void refreshDependentBundles( MavenProjectsBundleDeploymentPlan deploymentPlan ) {
      for( BundleDependency dependency : deploymentPlan.getExistingBundleDependencies() ) {
         getPackageAdmin().refreshPackages( new Bundle[]{ dependency.getExistingBundle() } );
         System.out.println( String.format( "Refreshed Bundle(%s): %s", dependency.getExistingBundle().getBundleId(), dependency.getExistingBundle().getSymbolicName() ) );
      }
   }

   private void diagnoseInstallFailure( AbstractBundleDeploymentPlan plan, Exception exception ) {
      try {
         System.out.println( "Diagnosing Install Exception..." );
         final List<UsesConflictError> conflictsFromMessage = parseUsesConflicts( exception );
         final List<UsesConflict> conflicts = new ArrayList<UsesConflict>();
         if( conflictsFromMessage.isEmpty() ) {
            conflicts.addAll( getOsgiAnalyzerService().findUsesConflicts( plan.getManifest().toDictionary() ) );
         }
         else {
            for( UsesConflictError error : conflictsFromMessage ) {
               conflicts.addAll( getOsgiAnalyzerService().findUsesConflicts( plan.getManifest().toDictionary(), error.getImportPackageName() ) );
            }
         }
         if( !conflicts.isEmpty() ) {
            System.out.println( "Uses Conflicts Found:" );
            for( UsesConflict conflict : conflicts ) {
               System.out.println( "   " + conflict );
            }
         }
         else {
            System.out.println( "No Uses Conflicts Found:" );
         }
      }
      catch( Throwable exception2 ) {
         LOG.error( "Failed to diagnose install failure", exception2 );
      }
   }

   private List<UsesConflictError> parseUsesConflicts( Exception exception ) {
      final String message = ExceptionUtils.getRootCauseMessage( exception );
      final List<UsesConflictError> result = new ArrayList<UsesConflictError>();
      final String[] array = message.split( "Uses violation:" );
      for( int index = 1; index < array.length; index += 2 ) {
         final String sub = array[index];
         final String searchText = "<Import-Package:";
         final int startIndex = sub.indexOf( searchText ) + searchText.length();
         final int endIndex = sub.indexOf( ";" );
         final String packageName = sub.substring( startIndex, endIndex ).trim();
         final int startIndex2 = sub.indexOf( "\"" ) + 1;
         final int endIndex2 = sub.indexOf( "\"", startIndex2 );
         final String version = sub.substring( startIndex2, endIndex2 ).trim();
         final String searchText2 = "bundle <";
         final int startIndex3 = sub.indexOf( searchText2 ) + searchText2.length();
         final int endIndex3 = sub.indexOf( "_", startIndex3 );
         final String bundleSymbolicName = sub.substring( startIndex3, endIndex3 ).trim();
         result.add( new UsesConflictError( packageName, version, bundleSymbolicName ) );
      }
      return result;
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

   private void addDeployed( AbstractBundleDeploymentPlan plan, Bundle bundle ) {
      if( plan instanceof MavenProjectBundleDeploymentPlan ) {
         final MavenProjectBundleDeploymentPlan mavenPlan = ( MavenProjectBundleDeploymentPlan )plan;
         final DeployedMavenProject deployed = new DeployedMavenProject();
         deployed.setBundle( bundle );
         deployed.setArtifact( MavenUtils.getAetherArtifact( mavenPlan.getMavenProjectHolder().getProject().getArtifact() ) );
         deployed.setMavenProjectFolder( mavenPlan.getMavenProjectHolder().getProject().getBasedir() );
         deployed.setChecksum( FileUtils.md5HexForDir( deployed.getMavenProjectFolder() ) );

         removeDeployed( deployed.getArtifact() );
         deployedMavenProjects.add( deployed );
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

   private DeployedMavenProject getDeployed( Artifact artifact ) {
      DeployedMavenProject result = null;
      for( DeployedMavenProject deployed : deployedMavenProjects ) {
         if( deployed.getArtifact().equals( artifact ) ) {
            result = deployed;
            break;
         }
      }
      return result;
   }

   private DeployedMavenProject getDeployed( Bundle bundle ) {
      DeployedMavenProject result = null;
      for( DeployedMavenProject deployed : deployedMavenProjects ) {
         if( deployed.getBundle().equals( bundle ) ) {
            result = deployed;
            break;
         }
      }
      return result;
   }

   private DeployedMavenProject getDeployed( File mavenProjectFolder ) {
      DeployedMavenProject result = null;
      for( DeployedMavenProject deployed : deployedMavenProjects ) {
         if( deployed.getMavenProjectFolder().equals( mavenProjectFolder ) ) {
            result = deployed;
            break;
         }
      }
      return result;
   }

   private File getMavenProjectBundleFolder( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() );
   }

   private URI getMavenProjectBundleUri( MavenProject project ) {
      try {
         return new URI( String.format( "assembly:%s", FileUtils.getPathForUri( getMavenProjectBundleFolder( project ) ) ) );
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
            if( isMavenProjectChanged( mavenProjectFolder ) ) {
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
            else {
               LOG.debug( String.format( "Skipping %s (Not Changed)", mavenProjectFolder.getName() ) );
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
         if( BundleUtils.isVirgoEnvironment( bundleContext ) && plan.isWebBundle() ) {
            if( plan.getFile().isDirectory() ) {
               final File jarFile = plan.createJarFile();
               final DeploymentIdentity id = getApplicationDeployer().install( jarFile.toURI() );
               result = BundleUtils.getBundleByNameOrId( bundleContext, id.getSymbolicName() );
            }
            else {
               final DeploymentIdentity id = getApplicationDeployer().install( plan.getFile().toURI() );
               result = BundleUtils.getBundleByNameOrId( bundleContext, id.getSymbolicName() );
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

   private boolean isDeployed( File mavenProjectFolder ) {
      return getDeployed( mavenProjectFolder ) != null;
   }

   private boolean isMavenProject( File folder ) {
      boolean result = folder.exists();
      result = result && folder.isDirectory();
      result = result && new File( folder.getAbsolutePath() + File.separator + "pom.xml" ).exists();
      return result;
   }

   private boolean isMavenProjectChanged( File mavenProjectFolder ) {
      boolean result = true;
      if( isDeployed( mavenProjectFolder ) ) {
         final String newChecksum = FileUtils.md5HexForDir( mavenProjectFolder );
         final String deployedChecksum = getDeployed( mavenProjectFolder ).getChecksum();
         result = !newChecksum.equals( deployedChecksum );
      }
      return result;
   }

   private boolean isOsgiBundle( MavenProject project ) {
      final File classesFolder = new File( project.getBuild().getOutputDirectory() );
      return ObrUtils.isOsgiBundle( classesFolder );
   }

   private boolean isUsesConflict( Bundle bundle ) {
      return !getOsgiAnalyzerService().findUsesConflicts( bundle ).isEmpty();
   }

   private void printDeploymentPlan( MavenProjectsBundleDeploymentPlan deploymentPlan, boolean showOptionalImports, boolean verbose ) {
      System.out.println( "" );
      System.out.println( "Deployment Plan" );
      System.out.println( "===============================================" );
      if( !deploymentPlan.getInstallOrder().isEmpty() ) {
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
      }
      else {
         System.out.println( "Nothing to deploy." );
      }
      System.out.println( "" );
      printRefreshExistingDependent( deploymentPlan, verbose );
      System.out.println( "" );
      printUnresolved( deploymentPlan, verbose, Resolution.MANDATORY );
      if( showOptionalImports ) {
         printUnresolved( deploymentPlan, verbose, Resolution.OPTIONAL );
      }
      System.out.println( "" );
      printValidationErrors( deploymentPlan, verbose );
   }

   private void printRefreshExistingDependent( MavenProjectsBundleDeploymentPlan deploymentPlan, boolean verbose ) {
      if( deploymentPlan.isExistingBundleDependencies() ) {
         System.out.println( "Existing Dependent Bundles" );
         System.out.println( "===============================================" );
         for( BundleDependency dependency : deploymentPlan.getExistingBundleDependencies() ) {
            System.out.println( String.format( "Refresh Bundle(%s): %s", dependency.getExistingBundle().getBundleId(), dependency.getExistingBundle().getSymbolicName() ) );
         }
      }
   }

   private void printDeploymentPlanDurations( MavenProjectsBundleDeploymentPlan deploymentPlan ) {
      printDuration( deploymentPlan.getInitDependencyPlansDuration(), "Init Dependency Plans" );
      printDuration( deploymentPlan.getInitProjectPlansDuration(), "Init Project Plans" );
      printDuration( deploymentPlan.getInitInstallOrderDuration(), "Init Install Order" );
      printDuration( deploymentPlan.getValidatePlansDuration(), "Validate Plans" );
      printDuration( deploymentPlan.getInitDependentBundlesDuration(), "Init Dependent Bundles" );
      printDuration( deploymentPlan.getInitStartOrderDuration(), "Init Start Order" );
   }

   private void printDuration( Duration duration, String description ) {
      if( duration != null ) {
         System.out.println( duration.getFormatted( description ) );
      }
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

   private void refreshBundleWithUsesConflicts( Bundle bundle ) {
      final Set<Bundle> bundles = new HashSet<Bundle>();
      bundles.add( bundle );
      for( UsesConflict usesConflict : getOsgiAnalyzerService().findUsesConflicts( bundle ) ) {
         bundles.add( usesConflict.getUsesConflictBundle() );
      }
      final Bundle[] refreshBundles = new Bundle[bundles.size()];
      bundles.toArray( refreshBundles );
      getPackageAdmin().refreshPackages( refreshBundles );
   }

   private void removeDeployed( Artifact artifact ) {
      final DeployedMavenProject existing = getDeployed( artifact );
      if( existing != null ) {
         deployedMavenProjects.remove( existing );
      }
   }

   private void removeDeployed( Bundle bundle ) {
      final DeployedMavenProject existing = getDeployed( bundle );
      if( existing != null ) {
         deployedMavenProjects.remove( existing );
      }
   }

   private Bundle update( AbstractBundleDeploymentPlan plan ) {
      try {
         final Bundle bundle = plan.getExistingBundle();
         if( bundle != null ) {
            if( BundleUtils.isVirgoEnvironment( bundleContext ) && plan.isWebBundle() ) {
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
