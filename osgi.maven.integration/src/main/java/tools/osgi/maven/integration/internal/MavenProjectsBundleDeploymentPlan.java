package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Defines a deployment plan for a maven project */
public class MavenProjectsBundleDeploymentPlan {
   public static abstract class AbstractBundleDeploymentPlan {
      private List<BundleImportRequirement> importRequirements = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleImportRequirement>();

      public void addBundleImportRequirement( BundleImportRequirement requirement ) {
         importRequirements.add( requirement );
      }

      public abstract URI getBundleUri();

      public List<BundleImportRequirement> getImportRequirements() {
         return importRequirements;
      }

      public List<BundleImportRequirement> getUnresolvedImportRequirements() {
         return getUnresolvedImportRequirements( null );
      }

      public List<BundleImportRequirement> getUnresolvedImportRequirements( Resolution resolution ) {
         final List<BundleImportRequirement> result = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleImportRequirement>();
         for( BundleImportRequirement requirement : importRequirements ) {
            if( resolution == null || requirement.getImportedPackage().getResolution().equals( resolution ) ) {
               if( !requirement.isResolved() ) {
                  result.add( requirement );
               }
            }
         }
         return result;
      }

      public boolean isResolved() {
         return isResolved( null );
      }

      public boolean isResolved( Resolution resolution ) {
         return getUnresolvedImportRequirements( resolution ).isEmpty();
      }

      public void setImportRequirements( List<BundleImportRequirement> importRequirements ) {
         this.importRequirements = importRequirements;
      }

      public boolean isDependentOn( AbstractBundleDeploymentPlan planB ) {
         boolean result = false;
         for( BundleImportRequirement requirement : getImportRequirements() ) {
            if( requirement.matches( planB ) ) {
               result = true;
               break;
            }
         }
         return result;
      }
   }

   public static class BundleImportRequirement {
      private Bundle existingBundle;
      private ImportedPackage importedPackage;
      private Artifact mavenDependency;
      private MavenProjectHolder mavenProject;
      private BundleImportRequirementResolveType resolveType = BundleImportRequirementResolveType.None;

      public BundleImportRequirement( ImportedPackage importedPackage ) {
         this.importedPackage = importedPackage;
      }

      @Override
      public boolean equals( Object other ) {
         boolean result = other != null && other instanceof BundleImportRequirement;
         result = result && getResolveType().equals( ( ( BundleImportRequirement )other ).getResolveType() );
         switch( resolveType ) {
         case ExistingBundle:
            result = result && existingBundle != null && existingBundle.equals( ( ( BundleImportRequirement )other ).getExistingBundle() );
            break;
         case MavenProject:
            result = result && mavenProject != null && mavenProject.equals( ( ( BundleImportRequirement )other ).getMavenProject() );
            break;
         case MavenDependency:
            result = result && mavenDependency != null && mavenDependency.equals( ( ( BundleImportRequirement )other ).getMavenDependency() );
            break;
         case None:
         }
         return result;
      }

      public Bundle getExistingBundle() {
         return existingBundle;
      }

      public ImportedPackage getImportedPackage() {
         return importedPackage;
      }

      public Artifact getMavenDependency() {
         return mavenDependency;
      }

      public MavenProjectHolder getMavenProject() {
         return mavenProject;
      }

      public String getResolveDescription() {
         String result = "";
         switch( resolveType ) {
         case ExistingBundle:
            result = existingBundle != null ? existingBundle.getSymbolicName() : "";
            break;
         case MavenProject:
            result = mavenProject != null ? mavenProject.getProject().getArtifactId() : "";
            break;
         case MavenDependency:
            result = mavenDependency != null ? mavenDependency.getArtifactId() : "";
            break;
         case None:
         }
         return result;
      }

      public BundleImportRequirementResolveType getResolveType() {
         return resolveType;
      }

      public boolean isResolved() {
         return !BundleImportRequirementResolveType.None.equals( resolveType );
      }

      public boolean matches( AbstractBundleDeploymentPlan otherPlan ) {
         boolean result = true;
         switch( resolveType ) {
         case ExistingBundle:
            result = false;
            break;
         case MavenProject:
            result = result && mavenProject != null && otherPlan instanceof MavenProjectBundleDeploymentPlan && mavenProject.equals( ( ( MavenProjectBundleDeploymentPlan )otherPlan ).getMavenProjectHolder() );
            break;
         case MavenDependency:
            result = result && mavenDependency != null && otherPlan instanceof MavenDependencyBundleDeploymentPlan && mavenDependency.equals( ( ( MavenDependencyBundleDeploymentPlan )otherPlan ).getDependency() );
            break;
         case None:
            result = false;
            break;
         }
         return result;
      }

      public void setExistingBundle( Bundle existingBundle ) {
         this.existingBundle = existingBundle;
         setResolveType( BundleImportRequirementResolveType.ExistingBundle );
      }

      public void setMavenDependency( Artifact existing ) {
         mavenDependency = existing;
         setResolveType( BundleImportRequirementResolveType.MavenDependency );
      }

      public void setMavenProject( MavenProjectHolder holder ) {
         this.mavenProject = holder;
         setResolveType( BundleImportRequirementResolveType.MavenProject );
      }

      public void setResolveType( BundleImportRequirementResolveType resolveType ) {
         this.resolveType = resolveType;
      }

      @Override
      public String toString() {
         return String.format( "Import Requirement(%s): %s(%s)", getImportedPackage().getPackageName(), getResolveType().name(), getResolveDescription() );
      }
   }

   public List<AbstractBundleDeploymentPlan> getDependentBundleDeploymentPlans( AbstractBundleDeploymentPlan plan ) {
      final List<AbstractBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      addDependentPlans( result, plan );
      return result;
   }

   public List<MavenProjectBundleDeploymentPlan> getDependentMavenProjectBundleDeploymentPlans( AbstractBundleDeploymentPlan plan ) {
      final List<AbstractBundleDeploymentPlan> all = getDependentBundleDeploymentPlans( plan );
      final List<MavenProjectBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan>();
      for( AbstractBundleDeploymentPlan dependent : all ) {
         if( dependent instanceof MavenProjectBundleDeploymentPlan ) {
            result.add( ( ( MavenProjectBundleDeploymentPlan )dependent ) );
         }
      }
      return result;
   }

   private void addDependentPlans( List<AbstractBundleDeploymentPlan> plans, AbstractBundleDeploymentPlan plan ) {
      for( AbstractBundleDeploymentPlan otherPlan : installOrder ) {
         if( !plan.equals( otherPlan ) && otherPlan.isDependentOn( plan ) && !plans.contains( otherPlan ) ) {
            plans.add( otherPlan );
            addDependentPlans( plans, otherPlan );
         }
      }
   }

   @SuppressWarnings({ "unchecked", "unused" })
   private <T extends AbstractBundleDeploymentPlan> T findMatchingBundleDeploymentPlan( BundleImportRequirement requirement ) {
      T result = null;
      for( AbstractBundleDeploymentPlan plan : installOrder ) {
         if( requirement.matches( plan ) ) {
            result = ( T )plan;
            break;
         }
      }
      return result;
   }

   public static enum BundleImportRequirementResolveType {
      /** Already installed bundle in the environment resolves the import */
      ExistingBundle,
      /** Maven dependency will be installed to resolve the requirement */
      MavenDependency,
      /** Another Maven Project will satisfy the the import requirement */
      MavenProject,
      /** No strategy to resolve the import could be found */
      None,
   }

   public static class MavenDependencyBundleDeploymentPlan extends AbstractBundleDeploymentPlan {
      private Artifact dependency;

      public MavenDependencyBundleDeploymentPlan( Artifact dependency ) {
         this.dependency = dependency;
      }

      @Override
      public boolean equals( Object other ) {
         boolean result = other != null && getClass().isAssignableFrom( other.getClass() );
         result = result && dependency != null && dependency.equals( ( ( MavenDependencyBundleDeploymentPlan )other ).getDependency() );
         return result;
      }

      @Override
      public URI getBundleUri() {
         return dependency.getFile().toURI();
      }

      public Artifact getDependency() {
         return dependency;
      }

      @Override
      public String toString() {
         return String.format( "Maven Dependency: %s", getDependency().getArtifactId() );
      }
   }

   public static class MavenProjectBundleDeploymentPlan extends AbstractBundleDeploymentPlan {
      private MavenProjectHolder mavenProjectHolder;

      public MavenProjectBundleDeploymentPlan( MavenProjectHolder holder ) {
         this.mavenProjectHolder = holder;
      }

      @Override
      public boolean equals( Object other ) {
         boolean result = other != null && getClass().isAssignableFrom( other.getClass() );
         result = result && mavenProjectHolder != null && mavenProjectHolder.equals( ( ( MavenProjectBundleDeploymentPlan )other ).getMavenProjectHolder() );
         return result;
      }

      @Override
      public URI getBundleUri() {
         try {
            return new URI( String.format( "assembly:%s", getMavenProjectBundleFolder().getAbsolutePath() ) );
         }
         catch( Exception exception ) {
            throw new RuntimeException( String.format( "Error getting maven project bundle URI for: %s", mavenProjectHolder.getProject().getArtifactId() ), exception );
         }
      }

      public MavenProjectHolder getMavenProjectHolder() {
         return mavenProjectHolder;
      }

      @Override
      public String toString() {
         return String.format( "Maven Project: %s", getMavenProjectHolder().getProject().getArtifactId() );
      }

      private File getMavenProjectBundleFolder() {
         return new File( mavenProjectHolder.getProject().getBuild().getOutputDirectory() );
      }
   }

   private BundleContext bundleContext;
   private List<MavenDependencyBundleDeploymentPlan> dependencyPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenDependencyBundleDeploymentPlan>();
   private List<AbstractBundleDeploymentPlan> installOrder = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
   private List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
   private List<MavenProjectBundleDeploymentPlan> projectPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan>();
   private Map<MavenProjectHolder, List<Artifact>> resolvedMavenDependencies = new HashMap<MavenProjectHolder, List<Artifact>>();

   public MavenProjectsBundleDeploymentPlan( BundleContext bundleContext, List<MavenProjectHolder> mavenProjects ) {
      this.bundleContext = bundleContext;
      this.mavenProjects = new ArrayList<MavenProjectHolder>( mavenProjects );
      init();
   }

   public List<MavenDependencyBundleDeploymentPlan> getDependencyPlans() {
      return dependencyPlans;
   }

   public List<AbstractBundleDeploymentPlan> getInstallOrder() {
      return installOrder;
   }

   public List<MavenProjectHolder> getMavenProjects() {
      return mavenProjects;
   }

   public List<MavenProjectBundleDeploymentPlan> getProjectPlans() {
      return projectPlans;
   }

   public List<AbstractBundleDeploymentPlan> getUnresolvedPlans() {
      return getUnresolvedPlans( null );
   }

   public List<AbstractBundleDeploymentPlan> getUnresolvedPlans( Resolution resolution ) {
      final List<AbstractBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      for( AbstractBundleDeploymentPlan plan : installOrder ) {
         if( !plan.isResolved( resolution ) ) {
            result.add( plan );
         }
      }
      return result;
   }

   public boolean isResolved() {
      return isResolved( null );
   }

   public boolean isResolved( Resolution resolution ) {
      return getUnresolvedPlans( resolution ).isEmpty();
   }

   public void setProjectPlans( List<MavenProjectBundleDeploymentPlan> projectPlans ) {
      this.projectPlans = projectPlans;
   }

   private void addMavenDependencyPlans( List<Artifact> dependencies ) {
      for( Artifact dependency : dependencies ) {
         final MavenDependencyBundleDeploymentPlan plan = new MavenDependencyBundleDeploymentPlan( dependency );
         final BundleManifest manifest = getBundleManifest( dependency );
         if( manifest == null ) {
            throw new RuntimeException( String.format( "No manifest could be loaded for Maven Dependency: %s", dependency.getArtifactId() ) );
         }
         final List<BundleImportRequirement> requirements = resolveBundleImportRequirements( manifest );
         plan.setImportRequirements( requirements );
         if( !dependencyPlans.contains( plan ) ) {
            dependencyPlans.add( plan );
            addMavenDependencyPlans( getUnplannedMavenDependencies( plan ) );
         }
      }
   }

   private boolean containsExportForImport( Bundle bundle, ImportedPackage importedPackage ) {
      return getExportedPackage( bundle, importedPackage ) != null;
   }

   private boolean containsExportForImport( BundleManifest manifest, ImportedPackage importedPackage ) {
      return getExportedPackage( manifest, importedPackage ) != null;
   }

   private Bundle findBestMatchBundleThatSatisfiesImport( ImportedPackage importedPackage ) {
      final List<Bundle> matches = findBundlesThatSatisfyImport( importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   private Artifact findBestMatchMavenDependencyThatSatisfiesImport( ImportedPackage importedPackage ) {
      final List<Artifact> matches = findMavenDependenciesThatSatisfyImport( importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   @SuppressWarnings("unused")
   private Artifact findBestMatchMavenDependencyThatSatisfiesImport( MavenProjectHolder holder, ImportedPackage importedPackage ) {
      final List<Artifact> matches = findMavenDependenciesThatSatisfyImport( holder, importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   private MavenProjectHolder findBestMatchMavenProjectThatSatisfiesImport( ImportedPackage importedPackage ) {
      final List<MavenProjectHolder> matches = findMavenProjectsThatSatisfyImport( importedPackage );
      return matches.isEmpty() ? null : matches.get( 0 );
   }

   private List<Bundle> findBundlesThatSatisfyImport( ImportedPackage importedPackage ) {
      final List<Bundle> result = new ArrayList<Bundle>();
      for( Bundle bundle : bundleContext.getBundles() ) {
         if( containsExportForImport( bundle, importedPackage ) ) {
            result.add( bundle );
         }
      }
      return result;
   }

   private List<Artifact> findMavenDependenciesThatSatisfyImport( ImportedPackage importedPackage ) {
      final List<Artifact> dependencies = resolveAllMavenDependencies();
      return findMavenDependenciesThatSatisfyImport( dependencies, importedPackage );
   }

   private List<Artifact> findMavenDependenciesThatSatisfyImport( List<Artifact> dependencies, ImportedPackage importedPackage ) {
      final List<Artifact> result = new ArrayList<Artifact>();
      for( Artifact dependency : dependencies ) {
         final BundleManifest manifest = getBundleManifest( dependency );
         if( manifest != null && containsExportForImport( manifest, importedPackage ) ) {
            result.add( dependency );
         }
      }
      return result;
   }

   private List<Artifact> findMavenDependenciesThatSatisfyImport( MavenProjectHolder holder, ImportedPackage importedPackage ) {
      try {
         final List<Artifact> dependencies = resolveMavenDependencies( holder );
         return findMavenDependenciesThatSatisfyImport( dependencies, importedPackage );
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error finding dependencies on maven project: %s", holder.getProject().getArtifactId() ), exception );
      }
   }

   private List<MavenProjectHolder> findMavenProjectsThatSatisfyImport( ImportedPackage importedPackage ) {
      final List<MavenProjectHolder> result = new ArrayList<MavenProjectHolder>();
      for( MavenProjectHolder holder : mavenProjects ) {
         final BundleManifest manifest = getBundleManifest( holder.getProject() );
         if( manifest != null && containsExportForImport( manifest, importedPackage ) ) {
            result.add( holder );
         }
      }
      return result;
   }

   private BundleManifest getBundleManifest( Artifact dependency ) {
      try {
         BundleManifest result = null;
         final JarFile jar = new JarFile( dependency.getFile() );
         final ZipEntry manifestEntry = jar.getEntry( JarFile.MANIFEST_NAME );
         if( manifestEntry != null ) {
            final Reader reader = new InputStreamReader( jar.getInputStream( manifestEntry ) );
            result = BundleManifestFactory.createBundleManifest( reader, new DummyParserLogger() );
            jar.close();
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error getting bundle manifest for dependency: %s", dependency ), exception );
      }
   }

   private BundleManifest getBundleManifest( Bundle bundle ) {
      return BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
   }

   private BundleManifest getBundleManifest( MavenProject project ) {
      try {
         BundleManifest result = null;
         final File manifestFile = getManifestFile( project );
         if( manifestFile.exists() ) {
            final Reader reader = new FileReader( manifestFile );
            result = BundleManifestFactory.createBundleManifest( reader, new DummyParserLogger() );
         }
         return result;
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Failed getting manifest for Maven Project: %s", project.getArtifactId() ) );
      }
   }

   private ExportedPackage getExportedPackage( Bundle bundle, ImportedPackage importedPackage ) {
      return getExportedPackage( getBundleManifest( bundle ), importedPackage );
   }

   private ExportedPackage getExportedPackage( BundleManifest manifest, ImportedPackage importedPackage ) {
      ExportedPackage result = null;
      if( manifest != null ) {
         final List<ExportedPackage> exportedPackages = manifest.getExportPackage() != null ? manifest.getExportPackage().getExportedPackages() : new ArrayList<ExportedPackage>();
         for( ExportedPackage exportedPackage : exportedPackages ) {
            if( exportedPackage.getPackageName().equals( importedPackage.getPackageName() ) ) {
               if( importedPackage.getVersion().includes( exportedPackage.getVersion() ) ) {
                  result = exportedPackage;
                  break;
               }
            }
         }
      }
      return result;
   }

   private File getManifestFile( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() + File.separator + "META-INF/MANIFEST.MF" );
   }

   private List<Artifact> getMavenDependenciesFromProjectPlans() {
      final Set<Artifact> result = new HashSet<Artifact>();
      for( MavenProjectBundleDeploymentPlan projectPlan : projectPlans ) {
         for( BundleImportRequirement requirement : projectPlan.getImportRequirements() ) {
            if( BundleImportRequirementResolveType.MavenDependency.equals( requirement.getResolveType() ) ) {
               result.add( requirement.getMavenDependency() );
            }
         }
      }
      return new ArrayList<Artifact>( result );
   }

   private List<Artifact> getUnplannedMavenDependencies( MavenDependencyBundleDeploymentPlan plan ) {
      final List<Artifact> subDependencies = new ArrayList<Artifact>();
      for( BundleImportRequirement requirement : plan.getImportRequirements() ) {
         if( BundleImportRequirementResolveType.MavenDependency.equals( requirement.getResolveType() ) ) {
            if( !hasMavenDependencyPlan( requirement ) ) {
               subDependencies.add( requirement.getMavenDependency() );
            }
         }
      }
      return subDependencies;
   }

   private boolean hasMavenDependencyPlan( BundleImportRequirement requirement ) {
      boolean result = false;
      if( BundleImportRequirementResolveType.MavenDependency.equals( requirement.getResolveType() ) ) {
         for( MavenDependencyBundleDeploymentPlan dependencyPlan : dependencyPlans ) {
            if( requirement.matches( dependencyPlan ) ) {
               result = true;
               break;
            }
         }
      }
      return result;
   }

   private void init() {
      initProjectPlans();
      initDependencyPlans();
      initInstallOrder();
   }

   private void initDependencyPlans() {
      dependencyPlans.clear();
      addMavenDependencyPlans( getMavenDependenciesFromProjectPlans() );
   }

   private void initInstallOrder() {
      installOrder.clear();
      installOrder.addAll( projectPlans );
      installOrder.addAll( dependencyPlans );
      sortInstallOrder( installOrder );
   }

   private void initProjectPlans() {
      projectPlans.clear();
      for( MavenProjectHolder holder : mavenProjects ) {
         final MavenProjectBundleDeploymentPlan plan = new MavenProjectBundleDeploymentPlan( holder );
         final BundleManifest manifest = getBundleManifest( holder.getProject() );
         if( manifest == null ) {
            throw new RuntimeException( String.format( "No manifest could be loaded for Maven Project: %s", holder.getProject().getArtifactId() ) );
         }
         final List<BundleImportRequirement> requirements = resolveBundleImportRequirements( manifest );
         plan.setImportRequirements( requirements );
         projectPlans.add( plan );
      }
   }

   private boolean isCircular( AbstractBundleDeploymentPlan planA, AbstractBundleDeploymentPlan planB ) {
      return planA.isDependentOn( planB ) && planB.isDependentOn( planA );
   }

   private List<Artifact> resolveAllMavenDependencies() {
      final Set<Artifact> result = new HashSet<Artifact>();
      for( MavenProjectHolder holder : mavenProjects ) {
         result.addAll( resolveMavenDependencies( holder ) );
      }
      return new ArrayList<Artifact>( result );
   }

   private List<BundleImportRequirement> resolveBundleImportRequirements( BundleManifest manifest ) {
      resolveAllMavenDependencies();
      final List<BundleImportRequirement> result = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleImportRequirement>();
      for( ImportedPackage importedPackage : manifest.getImportPackage().getImportedPackages() ) {
         final BundleImportRequirement requirement = new BundleImportRequirement( importedPackage );

         // Maven Project
         if( !requirement.isResolved() ) {
            final MavenProjectHolder existing = findBestMatchMavenProjectThatSatisfiesImport( importedPackage );
            if( existing != null ) {
               requirement.setMavenProject( existing );
            }
         }

         // Existing Bundle?
         if( !requirement.isResolved() ) {
            final Bundle existing = findBestMatchBundleThatSatisfiesImport( importedPackage );
            if( existing != null ) {
               requirement.setExistingBundle( existing );
            }
         }

         // Maven Dependency
         if( !requirement.isResolved() ) {
            final Artifact existing = findBestMatchMavenDependencyThatSatisfiesImport( importedPackage );
            if( existing != null ) {
               requirement.setMavenDependency( existing );
            }
         }
         result.add( requirement );
      }
      return result;
   }

   private List<Artifact> resolveMavenDependencies( MavenProjectHolder holder ) {
      if( !resolvedMavenDependencies.containsKey( holder ) ) {
         resolvedMavenDependencies.put( holder, MavenUtils.resolveDependencies( holder ) );
      }
      return resolvedMavenDependencies.get( holder );
   }

   private void sortInstallOrder( List<AbstractBundleDeploymentPlan> current ) {
      final List<AbstractBundleDeploymentPlan> ordered = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      boolean adjustedFlag = false;
      for( int index = 0; index < current.size(); index++ ) {
         final AbstractBundleDeploymentPlan currentPlan = current.get( index );
         for( BundleImportRequirement requirement : currentPlan.getImportRequirements() ) {
            for( int importIndex = index + 1; importIndex < current.size(); importIndex++ ) {
               final AbstractBundleDeploymentPlan otherPlan = current.get( importIndex );
               if( requirement.matches( otherPlan ) ) {
                  if( !ordered.contains( otherPlan ) && !isCircular( currentPlan, otherPlan ) ) {
                     adjustedFlag = true;
                     ordered.add( otherPlan );
                  }
               }
            }
         }
         if( !ordered.contains( currentPlan ) ) {
            ordered.add( currentPlan );
         }
      }
      if( adjustedFlag ) {
         sortInstallOrder( ordered );
      }
      current.clear();
      current.addAll( ordered );
   }
}
