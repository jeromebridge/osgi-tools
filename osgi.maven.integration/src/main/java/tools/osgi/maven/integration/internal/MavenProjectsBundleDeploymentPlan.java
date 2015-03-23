package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

import tools.osgi.analyzer.api.BundleUtils;
import tools.osgi.maven.integration.api.DeployedMavenProject;
import tools.osgi.maven.integration.api.JarBuilder;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.Resolution;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Defines a deployment plan for a maven project */
public class MavenProjectsBundleDeploymentPlan {
   public static abstract class AbstractBundleDeploymentPlan implements IBundleDeployment {
      private BundleContext bundleContext;
      private List<BundleDependency> existingBundleDependencies = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleDependency>();
      private List<BundleImportRequirement> importRequirements = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleImportRequirement>();
      private BundleManifest manifest;
      private List<AbstractBundleValidationError> validationErrors = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleValidationError>();
      private List<IBundleDeploymentPlanValidator> validators = new ArrayList<MavenProjectsBundleDeploymentPlan.IBundleDeploymentPlanValidator>();

      public AbstractBundleDeploymentPlan( BundleContext bundleContext, BundleManifest manifest ) {
         this.manifest = manifest;
         this.bundleContext = bundleContext;
         validators.add( new DuplicateImportsDeploymentPlanValidator() );
      }

      public void addBundleImportRequirement( BundleImportRequirement requirement ) {
         importRequirements.add( requirement );
      }

      public void addExistingBundleDependency( BundleDependency dependency ) {
         existingBundleDependencies.add( dependency );
      }

      public File createJarFile() {
         try {
            final File jarFile = File.createTempFile( "temp", ".jar" );
            final FileOutputStream fos = new FileOutputStream( jarFile );
            System.out.println( "Create Jar File: " + jarFile.toURI().toURL().toExternalForm() );

            final JarBuilder builder = new JarBuilder();
            builder.add( getFile() );
            builder.build( fos );
            return jarFile;
         }
         catch( Throwable exception ) {
            throw new RuntimeException( "Error creating jar file for plan: " + this, exception );
         }
      }

      public abstract URI getBundleUri();

      /** Existing Bundle in the OSGi container for this deployment plan */
      @Override
      public Bundle getExistingBundle() {
         try {
            Bundle result = null;
            if( BundleUtils.isVirgoEnvironment( bundleContext ) && isWebBundle() ) {
               result = BundleUtils.getBundleByNameOrId( bundleContext, getManifest().getBundleSymbolicName().getSymbolicName() );
            }
            else {
               result = bundleContext.getBundle( getBundleUri().toURL().toExternalForm() );
            }
            return result;
         }
         catch( Throwable exception ) {
            throw new RuntimeException( "Error finding existing bundle for plan: " + this, exception );
         }
      }

      public List<BundleDependency> getExistingBundleDependencies() {
         return existingBundleDependencies;
      }

      public abstract File getFile();

      public List<BundleImportRequirement> getImportRequirements() {
         return importRequirements;
      }

      @Override
      public BundleManifest getManifest() {
         return manifest;
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

      public List<AbstractBundleValidationError> getValidationErrors() {
         return validationErrors;
      }

      public boolean hasExistingBundle() {
         return getExistingBundle() != null;
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

      public boolean isResolved() {
         return isResolved( null );
      }

      public boolean isResolved( Resolution resolution ) {
         return getUnresolvedImportRequirements( resolution ).isEmpty();
      }

      public boolean isValidationError() {
         return !validationErrors.isEmpty();
      }

      public boolean isWebBundle() {
         return BundleUtils.isWebBundle( getManifest() );
      }

      public void setImportRequirements( List<BundleImportRequirement> importRequirements ) {
         this.importRequirements = importRequirements;
      }

      public void validate() {
         validationErrors.clear();
         for( IBundleDeploymentPlanValidator validator : validators ) {
            final List<AbstractBundleValidationError> errors = validator.validate( this );
            if( errors != null ) {
               validationErrors.addAll( errors );
            }
         }
      }

      protected void addValidationError( AbstractBundleValidationError error ) {
         validationErrors.add( error );
      }

      protected void addValidator( IBundleDeploymentPlanValidator validator ) {
         validators.add( validator );
      }
   }

   public static abstract class AbstractBundleValidationError {
      private BundleValidationType type = BundleValidationType.Null;

      public AbstractBundleValidationError( BundleValidationType type ) {
         this.type = type;
      }

      public BundleValidationType getType() {
         return type;
      }

      public abstract String getValidationMessage();

      @Override
      public String toString() {
         return getValidationMessage();
      }
   }

   /** Defines a dependency a bundle has on a bundle being deployed */
   public static class BundleDependency implements IBundleDeployment {
      private Bundle bundle;
      private BundleContext bundleContext;

      public BundleDependency( BundleContext bundleContext, Bundle bundle ) {
         this.bundleContext = bundleContext;
         this.bundle = bundle;
      }

      @Override
      public boolean equals( Object obj ) {
         boolean result = false;
         if( obj != null && obj instanceof BundleDependency ) {
            final BundleDependency other = ( BundleDependency )obj;
            result = bundle.equals( other.getExistingBundle() );
         }
         return result;
      }

      @Override
      public Bundle getExistingBundle() {
         Bundle result = bundle;
         if( bundle.getState() == Bundle.UNINSTALLED ) {
            result = findExistingBundle();
         }
         return ObjectUtils.defaultIfNull( result, bundle );
      }

      @Override
      public BundleManifest getManifest() {
         return BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
      }

      @Override
      public int hashCode() {
         final int prime = 31;
         int result = 1;
         result = prime * result + getClass().hashCode();
         result = prime * result + ( ( bundle == null ) ? 0 : bundle.hashCode() );
         return result;
      }

      @Override
      public String toString() {
         return String.format( "Bundle %s(%s)", bundle.getSymbolicName(), bundle.getBundleId() );
      }

      private Bundle findExistingBundle() {
         Bundle result = null;
         for( Bundle existing : bundleContext.getBundles() ) {
            if( existing.getLocation().equals( bundle.getLocation() ) ) {
               if( existing.getState() != Bundle.UNINSTALLED ) {
                  result = existing;
                  break;
               }
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

   public static enum BundleValidationType {
      /** Duplicate imports were found in the bundle manifest */
      DuplicateImports,
      /** Unknown or Not Applicable */
      Null, ;
   }

   /** Validates the manifest of the bundle does not have duplicate imports */
   public static class DuplicateImportsDeploymentPlanValidator implements IBundleDeploymentPlanValidator {
      @Override
      public List<AbstractBundleValidationError> validate( AbstractBundleDeploymentPlan plan ) {
         final List<AbstractBundleValidationError> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleValidationError>();
         final List<String> importPackagesFound = new ArrayList<String>();
         for( ImportedPackage importedPackage : plan.getManifest().getImportPackage().getImportedPackages() ) {
            if( importPackagesFound.contains( importedPackage.getPackageName() ) ) {
               result.add( new DuplicateImportsValidationError( importedPackage ) );
            }
            importPackagesFound.add( importedPackage.getPackageName() );
         }
         return result;
      }
   }

   public static class DuplicateImportsValidationError extends AbstractBundleValidationError {
      private ImportedPackage importedPackage;

      public DuplicateImportsValidationError( ImportedPackage importedPackage ) {
         super( BundleValidationType.DuplicateImports );
         this.importedPackage = importedPackage;
      }

      public ImportedPackage getImportedPackage() {
         return importedPackage;
      }

      @Override
      public String getValidationMessage() {
         return String.format( "Duplicate Import Found: %s", importedPackage.getPackageName() );
      }
   }

   public static interface IBundleDeployment {
      Bundle getExistingBundle();

      BundleManifest getManifest();
   }

   /** Interface used to perform validations on the deployment plan */
   public static interface IBundleDeploymentPlanValidator {
      public List<AbstractBundleValidationError> validate( AbstractBundleDeploymentPlan plan );
   }

   public static class MavenDependencyBundleDeploymentPlan extends AbstractBundleDeploymentPlan {
      private Artifact dependency;

      public MavenDependencyBundleDeploymentPlan( BundleContext bundleContext, Artifact dependency ) {
         super( bundleContext, MavenUtils.getBundleManifest( dependency ) );
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
         return getFile().toURI();
      }

      public Artifact getDependency() {
         return dependency;
      }

      @Override
      public File getFile() {
         return dependency.getFile();
      }

      @Override
      public String toString() {
         return String.format( "Maven Dependency: %s", getDependency().getArtifactId() );
      }
   }

   public static class MavenProjectBundleDeploymentPlan extends AbstractBundleDeploymentPlan {
      private MavenProjectHolder mavenProjectHolder;

      public MavenProjectBundleDeploymentPlan( BundleContext bundleContext, MavenProjectHolder holder ) {
         super( bundleContext, MavenUtils.getBundleManifest( holder.getProject() ) );
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
            return new URI( String.format( "assembly:%s", FileUtils.getPathForUri( getMavenProjectBundleFolder() ) ) );
         }
         catch( Exception exception ) {
            throw new RuntimeException( String.format( "Error getting maven project bundle URI for: %s", mavenProjectHolder.getProject().getArtifactId() ), exception );
         }
      }

      @Override
      public File getFile() {
         return getMavenProjectBundleFolder();
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

   public static abstract class AbstractReinstallDeploymentPlan extends AbstractBundleDeploymentPlan {

      public AbstractReinstallDeploymentPlan( BundleContext bundleContext, BundleManifest manifest ) {
         super( bundleContext, manifest );
      }
   }

   public static class ReinstallBundleDeploymentPlan extends AbstractReinstallDeploymentPlan {
      private Bundle existingBundle;

      public ReinstallBundleDeploymentPlan( BundleContext bundleContext, Bundle existingBundle ) {
         super( bundleContext, BundleManifestFactory.createBundleManifest( existingBundle.getHeaders(), new DummyParserLogger() ) );
         this.existingBundle = existingBundle;
      }

      @Override
      public URI getBundleUri() {
         try {
            return new URI( existingBundle.getLocation() );
         }
         catch( Throwable exception ) {
            throw new RuntimeException( String.format( "Failed to get URI for location: %s for bundle: %s(%s)", existingBundle.getLocation(), existingBundle.getSymbolicName(), existingBundle.getBundleId() ), exception );
         }
      }

      @Override
      public File getFile() {
         File result = null;
         if( "file".equalsIgnoreCase( getBundleUri().getScheme() ) ) {
            result = new File( getBundleUri() );
         }
         else {
            throw new RuntimeException( String.format( "URI: %s for Bundle: %s(%s) is not supported.", getBundleUri(), existingBundle.getSymbolicName(), existingBundle.getBundleId() ) );
         }
         return result;
      }

      @Override
      public String toString() {
         return String.format( "Reinstall Existing Bundle: %s(%s)", existingBundle.getSymbolicName(), existingBundle.getBundleId() );
      }
   }

   public static class ReinstallMavenProjectDeploymentPlan extends AbstractReinstallDeploymentPlan {
      private DeployedMavenProject deployedProject;

      public ReinstallMavenProjectDeploymentPlan( BundleContext bundleContext, DeployedMavenProject deployedProject ) {
         super( bundleContext, BundleManifestFactory.createBundleManifest( deployedProject.getBundle().getHeaders(), new DummyParserLogger() ) );
         this.deployedProject = deployedProject;
      }

      public DeployedMavenProject getDeployedProject() {
         return deployedProject;
      }

      @Override
      public URI getBundleUri() {
         return deployedProject.getBundleUri();
      }

      @Override
      public File getFile() {
         return deployedProject.getFile();
      }

      @Override
      public String toString() {
         return String.format( "Reinstall Existing Project: %s(%s)", deployedProject.getBundle().getSymbolicName(), deployedProject.getBundle().getBundleId() );
      }
   }

   private BundleContext bundleContext;
   private List<MavenDependencyBundleDeploymentPlan> dependencyPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenDependencyBundleDeploymentPlan>();
   private List<DeployedMavenProject> deployedMavenProjects = new ArrayList<DeployedMavenProject>();
   private boolean includeDependencies = false;
   private Duration initDependencyPlansDuration;
   private Duration initDependentBundlesDuration;
   private Duration initInstallOrderDuration;
   private Duration initReinstallPlansDuration;
   private Duration initProjectPlansDuration;
   private Duration initStartOrderDuration;
   private Duration initUninstallOrderDuration;
   private List<AbstractBundleDeploymentPlan> installOrder = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
   private List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
   private List<MavenProjectBundleDeploymentPlan> projectPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan>();
   private List<AbstractReinstallDeploymentPlan> reinstallPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractReinstallDeploymentPlan>();
   private Map<MavenProjectHolder, List<Artifact>> resolvedMavenDependencies = new HashMap<MavenProjectHolder, List<Artifact>>();
   private List<IBundleDeployment> startOrder = new ArrayList<MavenProjectsBundleDeploymentPlan.IBundleDeployment>();
   private List<IBundleDeployment> uninstallOrder = new ArrayList<MavenProjectsBundleDeploymentPlan.IBundleDeployment>();
   private Duration validatePlansDuration;
   private boolean reinstall;

   public List<AbstractReinstallDeploymentPlan> getReinstallPlans() {
      return reinstallPlans;
   }

   public Duration getInitReinstallPlansDuration() {
      return initReinstallPlansDuration;
   }

   public MavenProjectsBundleDeploymentPlan( BundleContext bundleContext, List<MavenProjectHolder> mavenProjects ) {
      this( bundleContext, mavenProjects, new ArrayList<DeployedMavenProject>(), false, false );
   }

   public Duration getInitUninstallOrderDuration() {
      return initUninstallOrderDuration;
   }

   public boolean isReinstall() {
      return reinstall;
   }

   public MavenProjectsBundleDeploymentPlan( BundleContext bundleContext, List<MavenProjectHolder> mavenProjects, List<DeployedMavenProject> deployedMavenProjects, boolean includeDependencies, boolean reinstall ) {
      this.bundleContext = bundleContext;
      this.mavenProjects = new ArrayList<MavenProjectHolder>( mavenProjects );
      this.deployedMavenProjects = new ArrayList<DeployedMavenProject>( deployedMavenProjects );
      this.includeDependencies = includeDependencies;
      this.reinstall = reinstall;
      init();
   }

   public List<MavenDependencyBundleDeploymentPlan> getDependencyPlans() {
      return dependencyPlans;
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

   public AbstractBundleDeploymentPlan getDeploymentPlanForExistingBundle( Bundle existing ) {
      AbstractBundleDeploymentPlan result = null;
      for( AbstractBundleDeploymentPlan plan : getAllPlans() ) {
         if( plan.hasExistingBundle() && existing.equals( plan.getExistingBundle() ) ) {
            result = plan;
            break;
         }
      }
      return result;
   }

   public List<AbstractBundleDeploymentPlan> getAllPlans() {
      final List<AbstractBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      result.addAll( dependencyPlans );
      result.addAll( projectPlans );
      result.addAll( reinstallPlans );
      return result;
   }

   public List<BundleDependency> getExistingBundleDependencies() {
      final Set<BundleDependency> result = new HashSet<MavenProjectsBundleDeploymentPlan.BundleDependency>();
      for( AbstractBundleDeploymentPlan plan : getAllPlans() ) {
         result.addAll( plan.getExistingBundleDependencies() );
      }
      return new ArrayList<MavenProjectsBundleDeploymentPlan.BundleDependency>( result );
   }

   public List<BundleDependency> getExistingBundleDependencies( IBundleDeployment deployed ) {
      final List<BundleDependency> all = getExistingBundleDependencies();
      final List<BundleDependency> result = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleDependency>();
      for( BundleDependency dependency : all ) {
         if( isDependentOn( dependency, deployed ) ) {
            result.add( dependency );
         }
      }
      return result;
   }

   public Duration getInitDependencyPlansDuration() {
      return initDependencyPlansDuration;
   }

   public Duration getInitDependentBundlesDuration() {
      return initDependentBundlesDuration;
   }

   public Duration getInitInstallOrderDuration() {
      return initInstallOrderDuration;
   }

   public Duration getInitProjectPlansDuration() {
      return initProjectPlansDuration;
   }

   public Duration getInitStartOrderDuration() {
      return initStartOrderDuration;
   }

   public List<AbstractBundleDeploymentPlan> getInstallOrder() {
      return installOrder;
   }

   public List<MavenProjectHolder> getMavenProjects() {
      return mavenProjects;
   }

   public List<AbstractBundleDeploymentPlan> getPlansWithValidationErrors() {
      final List<AbstractBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      for( AbstractBundleDeploymentPlan plan : getAllPlans() ) {
         if( plan.isValidationError() ) {
            result.add( plan );
         }
      }
      return result;
   }

   public List<MavenProjectBundleDeploymentPlan> getProjectPlans() {
      return projectPlans;
   }

   /**
    * Combines the deployment plans with bundles that are dependent
    * into a single ordered list they should be started
    * @return Order list bundles should be started for the deployment
    * plan.
    */
   public List<IBundleDeployment> getStartOrder() {
      return startOrder;
   }

   public List<AbstractBundleDeploymentPlan> getUnresolvedPlans() {
      return getUnresolvedPlans( null );
   }

   public List<AbstractBundleDeploymentPlan> getUnresolvedPlans( Resolution resolution ) {
      final List<AbstractBundleDeploymentPlan> result = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      for( AbstractBundleDeploymentPlan plan : getAllPlans() ) {
         if( !plan.isResolved( resolution ) ) {
            result.add( plan );
         }
      }
      return result;
   }

   public Duration getValidatePlansDuration() {
      return validatePlansDuration;
   }

   public boolean hasDeploymentPlanForExistingBundle( Bundle bundle ) {
      return getDeploymentPlanForExistingBundle( bundle ) != null;
   }

   public boolean isExistingBundleDependencies() {
      return !getExistingBundleDependencies().isEmpty();
   }

   public boolean isIncludeDependencies() {
      return includeDependencies;
   }

   public boolean isResolved() {
      return isResolved( null );
   }

   public boolean isResolved( Resolution resolution ) {
      return getUnresolvedPlans( resolution ).isEmpty();
   }

   public boolean isValidationErrors() {
      return !getPlansWithValidationErrors().isEmpty();
   }

   public void setProjectPlans( List<MavenProjectBundleDeploymentPlan> projectPlans ) {
      this.projectPlans = projectPlans;
   }

   private void addDependentPlans( List<AbstractBundleDeploymentPlan> plans, AbstractBundleDeploymentPlan plan ) {
      for( AbstractBundleDeploymentPlan otherPlan : getAllPlans() ) {
         if( !plan.equals( otherPlan ) && otherPlan.isDependentOn( plan ) && !plans.contains( otherPlan ) ) {
            plans.add( otherPlan );
            addDependentPlans( plans, otherPlan );
         }
      }
   }

   private void addMavenDependencyPlans( List<Artifact> dependencies ) {
      for( Artifact dependency : dependencies ) {
         final MavenDependencyBundleDeploymentPlan plan = new MavenDependencyBundleDeploymentPlan( bundleContext, dependency );
         final BundleManifest manifest = MavenUtils.getBundleManifest( dependency );
         if( manifest == null ) {
            throw new RuntimeException( String.format( "No manifest could be loaded for Maven Dependency: %s", dependency.getArtifactId() ) );
         }
         plan.setImportRequirements( resolveBundleImportRequirements( manifest ) );
         if( !dependencyPlans.contains( plan ) ) {
            dependencyPlans.add( plan );
            addMavenDependencyPlans( getUnplannedMavenDependencies( plan ) );
         }
      }
   }

   private void calculateDependentBundles() {
      final List<AbstractBundleDeploymentPlan> all = new ArrayList<MavenProjectsBundleDeploymentPlan.AbstractBundleDeploymentPlan>();
      all.addAll( dependencyPlans );
      all.addAll( projectPlans );
      for( AbstractBundleDeploymentPlan plan : all ) {
         try {
            plan.getExistingBundleDependencies().clear();
            if( plan.hasExistingBundle() ) {
               plan.getExistingBundleDependencies().addAll( getSecondaryBundleDependencies( plan.getExistingBundle() ) );
            }
         }
         catch( Exception exception ) {
            throw new RuntimeException( String.format( "Failed to initialize existing bundle dependencies for plan: %s", this ), exception );
         }
      }
   }

   private void calculateStartOrder() {
      startOrder.clear();
      startOrder.addAll( getAllPlans() );
      sort( startOrder );
   }

   private void calculateUninstallOrder() {
      uninstallOrder.clear();
      for( IBundleDeployment deployed : getAllPlans() ) {
         if( deployed.getExistingBundle() != null ) {
            uninstallOrder.add( deployed );
         }
      }
      sort( uninstallOrder );
      Collections.reverse( uninstallOrder );
   }

   public List<IBundleDeployment> getUninstallOrder() {
      return uninstallOrder;
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

   @SuppressWarnings({ "unchecked", "unused" })
   private <T extends AbstractBundleDeploymentPlan> T findMatchingBundleDeploymentPlan( BundleImportRequirement requirement ) {
      T result = null;
      for( AbstractBundleDeploymentPlan plan : getAllPlans() ) {
         if( requirement.matches( plan ) ) {
            result = ( T )plan;
            break;
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
         final BundleManifest manifest = MavenUtils.getBundleManifest( dependency );
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
         final BundleManifest manifest = MavenUtils.getBundleManifest( holder.getProject() );
         if( manifest != null && containsExportForImport( manifest, importedPackage ) ) {
            result.add( holder );
         }
      }
      return result;
   }

   private List<Artifact> getArtifactsToExcludeFromDependencuResolving() {
      final List<Artifact> result = new ArrayList<Artifact>();
      result.addAll( getMavenProjectArtifacts() );
      result.addAll( getDeployedMavenProjectArtifacts() );
      return result;
   }

   private BundleManifest getBundleManifest( Bundle bundle ) {
      return BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
   }

   private List<Artifact> getDeployedMavenProjectArtifacts() {
      final List<Artifact> result = new ArrayList<Artifact>();
      for( DeployedMavenProject deployed : deployedMavenProjects ) {
         result.add( deployed.getArtifact() );
      }
      return result;
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

   private List<Artifact> getMavenProjectArtifacts() {
      final List<Artifact> result = new ArrayList<Artifact>();
      for( MavenProjectHolder holder : getMavenProjects() ) {
         result.add( MavenUtils.getAetherArtifact( holder.getProject().getArtifact() ) );
      }
      return result;
   }

   private List<BundleDependency> getSecondaryBundleDependencies( Bundle bundle ) {
      final List<BundleDependency> result = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleDependency>();
      final Set<BundleDependency> dependents = new HashSet<MavenProjectsBundleDeploymentPlan.BundleDependency>();
      getSecondaryBundleDependencies( bundle, dependents );
      result.addAll( dependents );
      return result;
   }

   private void getSecondaryBundleDependencies( Bundle bundle, Set<BundleDependency> results ) {
      final BundleWiring wiring = bundle.adapt( BundleWiring.class );
      if( wiring != null ) {
         for( BundleWire provided : wiring.getProvidedWires( BundleRevision.PACKAGE_NAMESPACE ) ) {
            final Bundle dependentBundle = provided.getRequirerWiring().getBundle();
            if( !hasDeploymentPlanForExistingBundle( dependentBundle ) && !results.contains( dependentBundle ) ) {
               if( BundleUtils.isBundleResolved( dependentBundle ) ) {
                  results.add( new BundleDependency( bundleContext, dependentBundle ) );
                  results.addAll( getSecondaryBundleDependencies( dependentBundle ) );
               }
            }
         }
      }
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
      initDependentBundles();
      initReinstallPlans();
      initInstallOrder();
      validatePlans();
      initStartOrder();
      initUninstallOrder();
   }

   private void initReinstallPlans() {
      if( reinstall ) {
         final Date startTime = new Date();
         for( BundleDependency dependency : getExistingBundleDependencies() ) {
            final DeployedMavenProject deployedProject = getDeployedProject( dependency.getExistingBundle() );
            if( deployedProject != null ) {
               reinstallPlans.add( new ReinstallMavenProjectDeploymentPlan( bundleContext, deployedProject ) );
            }
            else {
               reinstallPlans.add( new ReinstallBundleDeploymentPlan( bundleContext, dependency.getExistingBundle() ) );
            }
         }
         initReinstallPlansDuration = new Duration( startTime, new Date() );
      }
   }

   private DeployedMavenProject getDeployedProject( Bundle existingBundle ) {
      DeployedMavenProject result = null;
      for( DeployedMavenProject deployed : deployedMavenProjects ) {
         if( deployed.getBundle().equals( existingBundle ) ) {
            result = deployed;
            break;
         }
      }
      return result;
   }

   private void initDependencyPlans() {
      final Date startTime = new Date();
      dependencyPlans.clear();
      if( includeDependencies ) {
         addMavenDependencyPlans( getMavenDependenciesFromProjectPlans() );
      }
      initDependencyPlansDuration = new Duration( startTime, new Date() );
   }

   private void initDependentBundles() {
      final Date startTime = new Date();
      calculateDependentBundles();
      initDependentBundlesDuration = new Duration( startTime, new Date() );
   }

   private void initInstallOrder() {
      final Date startTime = new Date();
      installOrder.clear();
      installOrder.addAll( getAllPlans() );
      sortPlans( installOrder );
      initInstallOrderDuration = new Duration( startTime, new Date() );
   }

   private void initProjectPlans() {
      final Date startTime = new Date();
      projectPlans.clear();
      for( MavenProjectHolder holder : mavenProjects ) {
         final MavenProjectBundleDeploymentPlan plan = new MavenProjectBundleDeploymentPlan( bundleContext, holder );
         final BundleManifest manifest = MavenUtils.getBundleManifest( holder.getProject() );
         if( manifest == null ) {
            throw new RuntimeException( String.format( "No manifest could be loaded for Maven Project: %s", holder.getProject().getArtifactId() ) );
         }
         plan.setImportRequirements( resolveBundleImportRequirements( manifest ) );
         projectPlans.add( plan );
      }
      initProjectPlansDuration = new Duration( startTime, new Date() );
   }

   private void initStartOrder() {
      final Date startTime = new Date();
      calculateStartOrder();
      initStartOrderDuration = new Duration( startTime, new Date() );
   }

   private void initUninstallOrder() {
      if( reinstall ) {
         final Date startTime = new Date();
         calculateUninstallOrder();
         initUninstallOrderDuration = new Duration( startTime, new Date() );
      }
   }

   private boolean isCircular( AbstractBundleDeploymentPlan planA, AbstractBundleDeploymentPlan planB ) {
      return planA.isDependentOn( planB ) && planB.isDependentOn( planA );
   }

   private boolean isCircular( IBundleDeployment deployA, IBundleDeployment deployB ) {
      return isDependentOn( deployA, deployB ) && isDependentOn( deployB, deployA );
   }

   private boolean isDependentOn( IBundleDeployment deployed, IBundleDeployment otherDeployed ) {
      boolean result = false;
      for( ImportedPackage importedPackage : deployed.getManifest().getImportPackage().getImportedPackages() ) {
         if( containsExportForImport( otherDeployed.getManifest(), importedPackage ) ) {
            result = true;
            break;
         }
      }
      return result;
   }

   private List<Artifact> resolveAllMavenDependencies() {
      final RepositorySystemSession session = MavenUtils.getRepositorySystemSession( bundleContext.getBundle().adapt( BundleWiring.class ).getClassLoader() );
      final Set<Artifact> result = new HashSet<Artifact>();
      for( MavenProjectHolder holder : mavenProjects ) {
         result.addAll( resolveMavenDependencies( holder, session ) );
      }
      return new ArrayList<Artifact>( result );
   }

   private List<BundleImportRequirement> resolveBundleImportRequirements( BundleManifest manifest ) {
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
         if( includeDependencies && !requirement.isResolved() ) {
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
      return resolveMavenDependencies( holder, null );
   }

   private List<Artifact> resolveMavenDependencies( MavenProjectHolder holder, RepositorySystemSession session ) {
      if( !resolvedMavenDependencies.containsKey( holder ) ) {
         resolvedMavenDependencies.put( holder, MavenUtils.resolveDependencies( holder, session, getArtifactsToExcludeFromDependencuResolving() ) );
      }
      return resolvedMavenDependencies.get( holder );
   }

   @SuppressWarnings("unused")
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

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void sortPlans( List<AbstractBundleDeploymentPlan> current ) {
      sort( ( List )current );
   }

   private void sort( List<IBundleDeployment> current ) {
      final List<IBundleDeployment> ordered = new ArrayList<MavenProjectsBundleDeploymentPlan.IBundleDeployment>();
      boolean adjustedFlag = false;
      for( int index = 0; index < current.size(); index++ ) {
         final IBundleDeployment deployed = current.get( index );
         // Find bundles in list that this one depends on and add them to the ordered list
         for( int importIndex = index + 1; importIndex < current.size(); importIndex++ ) {
            final IBundleDeployment otherDeployed = current.get( importIndex );
            if( isDependentOn( deployed, otherDeployed ) ) {
               if( !ordered.contains( otherDeployed ) && !isCircular( deployed, otherDeployed ) ) {
                  adjustedFlag = true;
                  ordered.add( otherDeployed );
               }
            }
         }
         if( !ordered.contains( deployed ) ) {
            ordered.add( deployed );
         }
      }
      if( adjustedFlag ) {
         sort( ordered );
      }
      current.clear();
      current.addAll( ordered );
   }

   private void validatePlans() {
      final Date startTime = new Date();
      for( AbstractBundleDeploymentPlan plan : getInstallOrder() ) {
         plan.validate();
      }
      validatePlansDuration = new Duration( startTime, new Date() );
   }

   public boolean isMavenProject( Bundle bundle ) {
      boolean result = false;
      if( bundle != null ) {
         for( MavenProjectBundleDeploymentPlan plan : projectPlans ) {
            if( plan.hasExistingBundle() && bundle.equals( plan.getExistingBundle() ) ) {
               result = true;
               break;
            }
         }
      }
      return result;
   }
}
