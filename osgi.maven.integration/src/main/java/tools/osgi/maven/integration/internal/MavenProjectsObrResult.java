package tools.osgi.maven.integration.internal;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.bundlerepository.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenProjectsObrResult extends AbstractMavenProjectObrResult {
   public static class MavenProjectObrResult extends AbstractMavenProjectObrResult {
      private MavenProjectHolder mavenProjectHolder;
      private Resource mavenProjectResource;

      public MavenProjectObrResult( MavenProjectHolder holder ) {
         setMavenProjectHolder( holder );
      }

      public MavenProjectHolder getMavenProjectHolder() {
         return mavenProjectHolder;
      }

      public Resource getMavenProjectResource() {
         return mavenProjectResource;
      }

      public void setMavenProjectHolder( MavenProjectHolder mavenProjectHolder ) {
         this.mavenProjectHolder = mavenProjectHolder;
      }

      public void setMavenProjectResource( Resource mavenProjectResource ) {
         this.mavenProjectResource = mavenProjectResource;
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger( MavenProjectObrResult.class );

   private Map<MavenProjectHolder, MavenProjectObrResult> mavenProjectResultMap = new HashMap<MavenProjectHolder, MavenProjectsObrResult.MavenProjectObrResult>();
   private List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
   private List<URI> mavenProjectsAdded = new ArrayList<URI>();

   public MavenProjectsObrResult( List<MavenProjectHolder> projects ) {
      setMavenProjects( projects );
   }

   public void addDependencyAdded( MavenProjectHolder holder, File file ) {
      addDependencyAdded( file );
      mavenProjectResultMap.get( holder ).addDependencyAdded( file );
   }

   public void addDependencyNotAdded( MavenProjectHolder holder, File file ) {
      addDependencyNotAdded( file );
      mavenProjectResultMap.get( holder ).addDependencyNotAdded( file );
   }

   public void addMavenDependencyResource( MavenProjectHolder holder, File jarFile, Resource resource ) {
      try {
         if( resource != null ) {
            if( !getDependenciesAdded().contains( jarFile ) ) {
               addResource( holder, resource );
               LOG.info( String.format( "Add Maven Project: %s, Dependency: %s to OBR", holder.getProject().getArtifactId(), jarFile.toURI().toURL().toExternalForm() ) );
               addDependencyAdded( holder, jarFile );
            }
         }
         else {
            LOG.warn( String.format( "Unable to add Maven Project: %s, Dependency: %s to OBR", holder.getProject().getArtifactId(), jarFile.toURI().toURL().toExternalForm() ) );
            addDependencyNotAdded( holder, jarFile );
         }
      }
      catch( Exception exception ) {
         throw new RuntimeException( String.format( "Error adding maven dependency for project: %s", holder.getProject().getArtifactId() ), exception );
      }
   }

   public void addMavenProjectResource( MavenProjectHolder holder, File bundleFolder, Resource resource ) {
      try {
         addResource( holder, resource );
         mavenProjectResultMap.get( holder ).setMavenProjectResource( resource );
         mavenProjectsAdded.add( new URI( resource.getURI() ) );
      }
      catch( Exception exception ) {
         throw new RuntimeException( "Error adding maven project resource", exception );
      }
   }

   public List<Resource> getMavenProjectResources() {
      final List<Resource> result = new ArrayList<Resource>();
      for( MavenProjectObrResult obrResult : mavenProjectResultMap.values() ) {
         if( obrResult.getMavenProjectResource() != null ) {
            result.add( obrResult.getMavenProjectResource() );
         }
      }
      return result;
   }

   public List<MavenProjectHolder> getMavenProjects() {
      return mavenProjects;
   }

   public List<URI> getMavenProjectsAdded() {
      return mavenProjectsAdded;
   }

   public void setMavenProjects( List<MavenProjectHolder> mavenProjects ) {
      this.mavenProjects = mavenProjects;
      mavenProjectResultMap.clear();
      for( MavenProjectHolder holder : mavenProjects ) {
         mavenProjectResultMap.put( holder, new MavenProjectObrResult( holder ) );
      }
   }

   private void addResource( MavenProjectHolder holder, Resource resource ) {
      getResources().add( resource );
      mavenProjectResultMap.get( holder ).getResources().add( resource );
   }
}
