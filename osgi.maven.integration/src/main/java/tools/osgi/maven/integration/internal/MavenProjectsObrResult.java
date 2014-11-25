package tools.osgi.maven.integration.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.felix.bundlerepository.Resource;

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

      public void setMavenProjectHolder( MavenProjectHolder mavenProjectHolder ) {
         this.mavenProjectHolder = mavenProjectHolder;
      }

      public Resource getMavenProjectResource() {
         return mavenProjectResource;
      }

      public void setMavenProjectResource( Resource mavenProjectResource ) {
         this.mavenProjectResource = mavenProjectResource;
      }
   }

   private List<MavenProjectHolder> mavenProjects = new ArrayList<MavenProjectHolder>();
   private Map<MavenProjectHolder, MavenProjectObrResult> mavenProjectResultMap = new HashMap<MavenProjectHolder, MavenProjectsObrResult.MavenProjectObrResult>();

   public MavenProjectsObrResult( List<MavenProjectHolder> projects ) {
      setMavenProjects( projects );
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

   public void addResource( MavenProjectHolder holder, Resource resource ) {
      getResources().add( resource );
      mavenProjectResultMap.get( holder ).getResources().add( resource );
   }

   public void addMavenProjectResource( MavenProjectHolder holder, Resource resource ) {
      addResource( holder, resource );
      mavenProjectResultMap.get( holder ).setMavenProjectResource( resource );
   }

   public void setMavenProjects( List<MavenProjectHolder> mavenProjects ) {
      this.mavenProjects = mavenProjects;
      mavenProjectResultMap.clear();
      for( MavenProjectHolder holder : mavenProjects ) {
         mavenProjectResultMap.put( holder, new MavenProjectObrResult( holder ) );
      }
   }

   public void addDependencyNotAdded( MavenProjectHolder holder, File file ) {
      addDependencyNotAdded( file );
      mavenProjectResultMap.get( holder ).addDependencyNotAdded( file );
   }

   public void addDependencyAdded( MavenProjectHolder holder, File file ) {
      addDependencyAdded( file );
      mavenProjectResultMap.get( holder ).addDependencyAdded( file );
   }
}
