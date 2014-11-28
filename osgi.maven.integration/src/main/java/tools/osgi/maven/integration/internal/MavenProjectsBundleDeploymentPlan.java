package tools.osgi.maven.integration.internal;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.project.MavenProject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.springsource.util.osgi.manifest.BundleManifest;
import com.springsource.util.osgi.manifest.BundleManifestFactory;
import com.springsource.util.osgi.manifest.ExportedPackage;
import com.springsource.util.osgi.manifest.ImportedPackage;
import com.springsource.util.osgi.manifest.parse.DummyParserLogger;

/** Defines a deployment plan for a maven project */
public class MavenProjectsBundleDeploymentPlan {
   public static class BundleImportRequirement {
      private Bundle existingBundle;
      private ImportedPackage importedPackage;
      private BundleImportRequirementResolveType resolveType = BundleImportRequirementResolveType.None;
      private MavenProjectHolder mavenProject;

      public BundleImportRequirement( ImportedPackage importedPackage ) {
         this.importedPackage = importedPackage;
      }

      public Bundle getExistingBundle() {
         return existingBundle;
      }

      public ImportedPackage getImportedPackage() {
         return importedPackage;
      }

      public BundleImportRequirementResolveType getResolveType() {
         return resolveType;
      }

      public boolean isResolved() {
         return !BundleImportRequirementResolveType.None.equals( resolveType );
      }

      public void setExistingBundle( Bundle existingBundle ) {
         this.existingBundle = existingBundle;
         setResolveType( BundleImportRequirementResolveType.ExistingBundle );
      }

      public void setResolveType( BundleImportRequirementResolveType resolveType ) {
         this.resolveType = resolveType;
      }

      public void setMavenProject( MavenProjectHolder holder ) {
         this.mavenProject = holder;
         setResolveType( BundleImportRequirementResolveType.MavenProject );
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
            result = "";
            break;
         case None:
         }
         return result;
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

   public static class MavenProjectBundleDeploymentPlan {
      private MavenProjectHolder mavenProjectHolder;
      private List<BundleImportRequirement> importRequirements = new ArrayList<MavenProjectsBundleDeploymentPlan.BundleImportRequirement>();

      public void addBundleImportRequirement( BundleImportRequirement requirement ) {
         importRequirements.add( requirement );
      }

      public List<BundleImportRequirement> getImportRequirements() {
         return importRequirements;
      }

      public void setImportRequirements( List<BundleImportRequirement> importRequirements ) {
         this.importRequirements = importRequirements;
      }

      public MavenProjectBundleDeploymentPlan( MavenProjectHolder holder ) {
         this.mavenProjectHolder = holder;
      }

      public MavenProjectHolder getMavenProjectHolder() {
         return mavenProjectHolder;
      }
   }

   private BundleContext bundleContext;
   private List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
   private List<MavenProjectBundleDeploymentPlan> projectPlans = new ArrayList<MavenProjectsBundleDeploymentPlan.MavenProjectBundleDeploymentPlan>();

   public MavenProjectsBundleDeploymentPlan( BundleContext bundleContext, List<MavenProjectHolder> mavenProjects ) {
      this.bundleContext = bundleContext;
      this.mavenProjects = new ArrayList<MavenProjectHolder>( mavenProjects );
      init();
   }

   public List<MavenProjectHolder> getMavenProjects() {
      return mavenProjects;
   }

   public List<MavenProjectBundleDeploymentPlan> getProjectPlans() {
      return projectPlans;
   }

   public void setProjectPlans( List<MavenProjectBundleDeploymentPlan> projectPlans ) {
      this.projectPlans = projectPlans;
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
      final List<ExportedPackage> exportedPackages = manifest.getExportPackage().getExportedPackages();
      for( ExportedPackage exportedPackage : exportedPackages ) {
         if( exportedPackage.getPackageName().equals( importedPackage.getPackageName() ) ) {
            if( importedPackage.getVersion().includes( exportedPackage.getVersion() ) ) {
               result = exportedPackage;
               break;
            }
         }
      }
      return result;
   }

   private BundleManifest getBundleManifest( Bundle bundle ) {
      return BundleManifestFactory.createBundleManifest( bundle.getHeaders(), new DummyParserLogger() );
   }

   private File getManifestFile( MavenProject project ) {
      return new File( project.getBuild().getOutputDirectory() + File.separator + "META-INF/MANIFEST.MF" );
   }

   private void init() {
      projectPlans.clear();
      for( MavenProjectHolder holder : mavenProjects ) {
         final MavenProjectBundleDeploymentPlan plan = new MavenProjectBundleDeploymentPlan( holder );
         final BundleManifest manifest = getBundleManifest( holder.getProject() );
         if( manifest == null ) {
            throw new RuntimeException( String.format( "No manifest could be loaded for Maven Project: %s", holder.getProject().getArtifactId() ) );
         }
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

            }
            plan.addBundleImportRequirement( requirement );
         }
         projectPlans.add( plan );
      }

      // 1. Get All Imported Packages (Resolve Maven Project Dependency Bundles)
      // 2. Determine If Bundle already exists to satisfy it
      //    3. If none; determine if a maven dependency that is a bundle satisfies it
      //        4. If none; mark as not resolved

   }
}
