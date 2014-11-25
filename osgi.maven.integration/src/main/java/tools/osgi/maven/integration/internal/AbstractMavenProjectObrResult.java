package tools.osgi.maven.integration.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.felix.bundlerepository.Resource;

public abstract class AbstractMavenProjectObrResult {

   private Set<File> dependenciesAdded = new HashSet<File>();
   private Set<File> dependenciesNotAdded = new HashSet<File>();
   private List<Resource> resources = new ArrayList<Resource>();

   public void addDependencyAdded( File file ) {
      dependenciesAdded.add( file );
   }

   public void addDependencyNotAdded( File file ) {
      dependenciesNotAdded.add( file );
   }

   public Set<File> getDependenciesAdded() {
      return dependenciesAdded;
   }

   public Set<File> getDependenciesNotAdded() {
      return dependenciesNotAdded;
   }

   public List<Resource> getResources() {
      return resources;
   }

   public void setDependenciesAdded( Set<File> dependenciesAdded ) {
      this.dependenciesAdded = dependenciesAdded;
   }

   public void setDependenciesNotAdded( Set<File> dependenciesNotAdded ) {
      this.dependenciesNotAdded = dependenciesNotAdded;
   }

   public void setResources( List<Resource> resources ) {
      this.resources = resources;
   }
}
